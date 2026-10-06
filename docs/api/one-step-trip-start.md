# One-step trip start — API contract

Starting a trip is one call. The app shows a local "ready to start" screen; nothing is
saved until the user presses **Start**, which creates the trip, makes it live
(`IN_PROGRESS`) and records the first check-in (`TRIP_STARTED`) in one transaction.
New clients never create `CREATED` (Draft) trips.

Error bodies follow the existing convention: **plain-text** message (not JSON), except 404
which has an empty body.

All endpoints need `Authorization: Bearer <access token>` (role USER or ADMIN).

---

## 1. Start a trip — `POST /api/1/trips/start` (command service, :8081)

### Headers

| Header | Required | Notes |
|---|---|---|
| `Idempotency-Key` | yes | 1–100 chars. Generate one (e.g. a UUID) when the ready screen opens and reuse it for every retry of that Start press. Scoped per user. |

### Body

```json
{
  "name": "Camino day 1",
  "visibility": "PUBLIC",
  "tripModality": "SIMPLE",
  "automaticUpdates": true,
  "updateRefresh": 900,
  "location": { "lat": 52.0907, "lon": 5.1214 },
  "battery": 82,
  "message": "Buen Camino!",
  "tripPlanId": null
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string 3–100 | yes, unless `tripPlanId` is set | Ignored when `tripPlanId` is set (plan name wins). |
| `visibility` | `PUBLIC` \| `PROTECTED` \| `PRIVATE` | yes | public / friends / only me. |
| `tripModality` | `SIMPLE` \| `MULTI_DAY` | no | Single-day / multi-day. Default `SIMPLE`. Ignored when `tripPlanId` is set (plan type wins). |
| `automaticUpdates` | boolean | yes | Auto check-in on/off. |
| `updateRefresh` | int, **seconds**, >= 60 | no | Auto check-in interval. Same unit as `PATCH /trips/{id}/settings`. Default 900 when `automaticUpdates` is true. |
| `location` | `{lat, lon}` | yes | Current location; becomes the `TRIP_STARTED` check-in. |
| `battery` | int 0–100 | no | |
| `message` | string <= 500 | no | Message on the first check-in. Default `"Trip Started!"`. |
| `tripPlanId` | UUID | no | Start from a plan: name, route (start/end/waypoints/planned polyline), dates and type come from the plan. Replaces `POST /trips/from-plan/{id}`. |

The first check-in is enriched exactly like a manual check-in (city/country via reverse
geocoding, weather, battery).

### Responses

`201 Created` — trip created and live:

```json
{
  "tripId": "0b8f9c0e-6c3e-4c39-9d3a-3b0b6f7c1a11",
  "tripUpdateId": "6c1f2a77-3b5e-4a7e-8a0c-1f5b2e9d4c22",
  "status": "IN_PROGRESS",
  "replayed": false
}
```

`200 OK` — same `Idempotency-Key` already used by this user: no new trip; the original
trip is returned with `"replayed": true` and its **current** `status`. The key is not
checked against the payload; reusing a key with a different body returns the original trip.

| Status | When | Body |
|---|---|---|
| 400 | Missing/blank/too long `Idempotency-Key`, `name` missing without plan, invalid field | plain-text message |
| 403 | `tripPlanId` belongs to another user | plain-text message |
| 404 | `tripPlanId` not found | empty |
| 409 | User already has an ongoing trip (`IN_PROGRESS`, `PAUSED` or `RESTING`) | `User already has a trip in progress. Only one trip can be in progress at a time.` |

On any error nothing is saved (no trip, no check-in).

The response is returned after commit, so `GET /api/1/trips/{tripId}` (query service) can be
called immediately.

---

## 2. Prefill defaults — `GET /api/1/trips/me/start-defaults` (query service, :8082)

Values to prefill the ready screen, taken from the user's most recent trip. Brand-new users
(or missing fields on old trips) get the defaults shown.

```json
{
  "visibility": "PUBLIC",
  "automaticUpdates": true,
  "updateRefresh": 900,
  "tripModality": "SIMPLE",
  "fromLastTrip": false
}
```

| Field | Default for new users |
|---|---|
| `visibility` | `PUBLIC` |
| `automaticUpdates` | `true` |
| `updateRefresh` | `900` (seconds) |
| `tripModality` | `SIMPLE` |
| `fromLastTrip` | `false` (`true` when values came from a previous trip) |

---

## 3. Check-ins are only accepted on live trips — `POST /api/1/trips/{tripId}/updates`

Unchanged request/response. New rule — the trip status must allow the check-in, otherwise
**409** with body `Check-ins are not allowed for a trip in status <STATUS>`:

| Trip status | Allowed `updateType` |
|---|---|
| `IN_PROGRESS` | any |
| `PAUSED` | any |
| `RESTING` | `DAY_END` only (the marker sent right after toggle-day) |
| `FINISHED` | `TRIP_ENDED` only (the marker sent right after finishing) |
| `CREATED` (Draft) | none |

**Client action:** the auto check-in chain should stop on 409 (like it does on 403/404).

---

## 4. Analytics — `POST /api/1/analytics/events` (command service, :8081)

There is no product-analytics vendor in the stack; events are Micrometer counters exposed on
`/actuator/prometheus` as `wanderer_trip_start_funnel_total{event, source}`.

```json
{ "event": "READY_SCREEN_VIEWED", "source": "SCRATCH" }
```

| Field | Values |
|---|---|
| `event` (required) | `READY_SCREEN_VIEWED`, `CLOSED_WITHOUT_STARTING`, `SAVED_AS_PLAN` |
| `source` (optional) | `SCRATCH`, `PLAN` |

Response `204 No Content`; `400` for unknown values or `TRIP_STARTED`. Without `source` the
counter is tagged `source="NONE"`.

`TRIP_STARTED` (with `source` `SCRATCH`/`PLAN`) is emitted **by the server** from
`POST /trips/start` (not on replays) — clients must not send it.

Prometheus example: `sum by (event, source) (increase(wanderer_trip_start_funnel_total[7d]))`.

---

## 5. Deprecated (kept for older app versions)

| Endpoint | Replacement |
|---|---|
| `POST /api/1/trips` (creates a Draft) | `POST /api/1/trips/start` |
| `POST /api/1/trips/from-plan/{tripPlanId}` (creates a Draft) | `POST /api/1/trips/start` with `tripPlanId` |

Both still work unchanged, are marked `deprecated` in OpenAPI and return a
`Deprecation: true` response header.

---

## 6. Admin: migrate Drafts to plans — `POST /api/1/admin/trips/drafts/migrate?dryRun=true`

Admin only. `dryRun` defaults to `true` (report only, writes nothing). Converts Draft
(`CREATED`) trips into trip plans keeping name, type, dates and visibility (stored in the
plan's `metadata.visibility`; plans have no visibility column), then removes the Draft.

```json
{
  "dryRun": true,
  "totalDrafts": 12,
  "toConvert": 3,
  "alreadyPlanned": 4,
  "converted": 0,
  "withCheckIns": [
    { "tripId": "…", "userId": "…", "name": "Tuvi Trip Plan", "checkIns": 16 }
  ],
  "missingRoute": [
    { "tripId": "…", "userId": "…", "name": "Weekend walk", "checkIns": 0 }
  ]
}
```

- `toConvert`: Drafts with start and end location, no check-ins, no existing plan → become a new plan.
- `alreadyPlanned`: Drafts created from a plan that still exists, no check-ins → Draft removed (the plan already holds the data).
- `withCheckIns`: never converted or deleted automatically; listed for manual review.
- `missingRoute`: no start/end location (plans require both) → left untouched, listed for review.

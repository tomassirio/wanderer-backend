# Live track recording — design

Date: 2026-10-08 · Branch: `feature/live-track-recording` (backend + frontend)

## Problem

Today the only location data is the `TripUpdate` ("check-in"), sent at most every
`updateRefresh` seconds (default 900). Each check-in synchronously calls Google
Geocoding, Weather and Distance Matrix inside the request transaction, then
Directions asynchronously to snap the route. Result: a single-day trip gets a
coarse, road-snapped route; a check-in that fails offline is lost; check-ins are
slow and expensive.

## Model

Split location data into two concepts:

- **Track point** — a raw GPS fix recorded on the phone. Dense, silent, never in the
  timeline. Drives the route line and the distance. Recorded locally, uploaded in
  batches. No Google calls per point.
- **Check-in** — the existing `TripUpdate` (message, city, weather, lifecycle
  markers, battery). Unchanged in meaning, but now idempotent, carries the phone's
  capture time, and is enriched asynchronously.

The phone records locally and only talks to the backend when a batch is due or a
check-in is posted. Everything on the phone works offline; uploads catch up later.

## Recording profiles (phone)

| Profile | Default for | Fix interval | Min distance | Batch delivery (`maxUpdateDelay`) | Upload every | Auto check-in |
|---|---|---|---|---|---|---|
| `LIVE` | `SIMPLE` (single-day) trips | 10 s | 10 m | 60 s | 2 min | off |
| `SAVER` | `MULTI_DAY` trips | 120 s | 25 m | 15 min | `updateRefresh` (default 900 s) | every `updateRefresh` (today's behavior) |

- Both use the fused location provider at `PRIORITY_HIGH_ACCURACY` with hardware
  batching (`setMaxUpdateDelayMillis`), so the CPU sleeps between deliveries.
- The user can switch profile while the trip is live (trip settings / live
  notification). The choice is stored locally per trip; the backend does not need it.
- These numbers are tuning knobs, kept as named constants in one place.

## Backend contract

### Track points

`POST /api/1/trips/{tripId}/track-points` (command, owner only, `USER`/`ADMIN`)

```json
{
  "points": [
    { "id": "uuid (client-generated)", "lat": 52.09, "lon": 5.12,
      "accuracyM": 8.0, "altitudeM": 3.1, "recordedAt": "2026-10-08T09:15:02Z" }
  ]
}
```

- 1..500 points per request; `lat`/`lon`/`recordedAt`/`id` required.
- Idempotent: points whose `id` already exists are ignored (`ON CONFLICT DO NOTHING`).
- Accepted while the trip is `IN_PROGRESS`, `RESTING` or `PAUSED` (late batches
  after a pause/rest still land). `FINISHED`: accepted only if `recordedAt` is not
  after the finish time (final flush). Other statuses → 409.
- Response `202 Accepted` `{ "accepted": <newly inserted count> }`.
- Stored in new table `trip_track_points` (`id` uuid PK, `trip_id` FK + index
  `(trip_id, recorded_at)`, `lat`, `lon`, `accuracy_m`, `altitude_m`,
  `recorded_at`, `received_at`). Liquibase changelog in `wanderer-command`.

After commit, asynchronously per trip (serialized per trip, no concurrent runs for
the same trip):

1. Load the trip's points ordered by `recorded_at`.
2. Drop jitter: skip a point closer than `max(accuracy, 5 m)` to the previous kept
   point, and points with `accuracyM > 100`.
3. Distance = Haversine sum over kept points → `trip.cachedDistanceKm`.
4. Polyline = encode kept points (simplify with Douglas-Peucker, ~5 m tolerance, so
   long trips stay small) → `trip.encodedPolyline`, `polylineUpdatedAt`.
   **No Directions or Distance Matrix calls for trips that have track points.**
5. Publish `PolylineUpdatedEvent` (existing; drives thumbnail + `POLYLINE_UPDATED`).
6. Broadcast new websocket event `TRACK_UPDATED` on the trip topic:
   `{ tripId, points: [{lat, lon, recordedAt}] /* the newly accepted ones */, distanceKm }`.

Incremental computation is allowed as an optimisation (append points newer than the
last processed `recorded_at`) as long as the result equals a full recompute.

`GET /api/1/trips/{tripId}/track-points?since=<ISO instant>` (query, same visibility
rules as trip updates) → `[{ "lat", "lon", "recordedAt" }]` ordered by `recordedAt`.
Without `since` returns all. Used by followers to backfill and by the owner on a new
device.

### Check-ins (`TripUpdate`)

`POST /api/1/trips/{tripId}/updates` gains two optional fields:

- `id` (uuid) — client-generated; if a `TripUpdate` with this id exists, return
  `202` with that id and do nothing (safe retries from the offline queue).
- `recordedAt` (instant) — when the phone captured it; becomes `timestamp`.
  Falls back to server `now()` when absent. Rejected (400) if more than 5 minutes in
  the future.
- `location` becomes optional for non-`REGULAR` update types (lifecycle markers
  must not be lost to a missing GPS fix). Still required for `REGULAR`.

Request path no longer calls Google. It validates, saves the row with `city`,
`country`, `temperatureCelsius`, `weatherCondition` null, broadcasts
`TRIP_UPDATED`, and returns `202` fast. After commit, asynchronously: reverse
geocode + weather lookup, save, broadcast new event `TRIP_UPDATE_ENRICHED`
`{ tripId, tripUpdateId, city, country, temperatureCelsius, weatherCondition }`.
Skip both lookups (copy values from the previous enriched check-in) when the new
location is within 300 m and 30 min of it.

`distanceSoFarKm` on a check-in = the trip's track distance at `recordedAt` when the
trip has track points; otherwise the existing segment logic (old clients).

`TRIP_UPDATED` payload gains `tripUpdateId` and `timestamp`.

Google client timeouts: `GeoApiContext` connect/read 5 s, retry timeout 10 s;
weather `RestClient` connect/read 5 s.

### Backward compatibility

Released apps (≤ 2.2.x) never send track points, `id` or `recordedAt`. For trips
without track points, the polyline/distance keep today's behaviour (Directions
append + Distance Matrix). Old clients keep working unchanged except that city/weather
arrive via `TRIP_UPDATE_ENRICHED` (they ignore it and see the values on next load).

## Frontend (Android only; web/iOS unchanged)

- **Recorder (native, `TripTrackingService.kt`)** — the foreground service now
  records: `FusedLocationProviderClient` with the active profile's request; each
  delivered fix gets a UUID and is inserted into SQLite `wanderer_track.db`, table
  `track_points(id TEXT PK, trip_id, lat, lon, accuracy_m, altitude_m, recorded_at
  INTEGER ms, synced INTEGER 0/1)`. Start/stop/profile change via the existing
  method channel (`startTracking` gains `tripId`, `profile`; new `setProfile`).
  Recording runs while the trip is `IN_PROGRESS`; stops on rest/pause/finish.
  Requires the background location permission flow that already exists.
- **Local store (Dart, `sqflite`)** — opens the same DB file. Also holds
  `pending_checkins(id TEXT PK, trip_id, json, created_at)` — the check-in outbox.
- **Sync (Dart)** — `TrackSyncService.flush(tripId)`: upload unsynced points in
  batches of 500, mark synced on 202; then drain pending check-ins (each with its
  client `id` and `recordedAt`). Triggered by: the existing WorkManager chain (now
  scheduled at the profile's upload interval), app resume, after posting a
  check-in, and on rest/pause/finish (final flush before the status change).
  4xx 403/404/409 on a trip → drop that trip's queue; other failures → keep and
  retry next trigger.
- **Check-ins** — `TripUpdateService.sendUpdate` writes to the outbox with a client
  id and capture time, then flushes. The UI shows the check-in immediately as
  pending; offline is not an error anymore ("Will send when online").
  Lifecycle markers are sent even without a GPS fix.
- **Live UI** — the owner's trip detail map draws the route from the local track
  (live while recording); followers extend the route from `TRACK_UPDATED` and
  backfill with `GET …/track-points?since=`. City/weather on a timeline item fill
  in on `TRIP_UPDATE_ENRICHED`. Profile switch (Live / Battery saver) in the live
  trip UI; default from trip modality.

## Out of scope

iOS recorder, auto-pause detection, elevation profile UI, GPX export, per-point
privacy zones. (Each can be added on top of track points later.)

## Testing

- Backend: unit tests for jitter filter, Haversine distance, Douglas-Peucker,
  idempotent insert, status rules, check-in idempotency/`recordedAt`, async
  enrichment + skip-when-close; existing tests keep passing.
- Frontend: unit tests for the outbox/sync logic (batching, mark-synced, drop on
  403/404/409, retry otherwise) and profile defaults; existing tests keep passing.
  Native recorder verified manually on a device.

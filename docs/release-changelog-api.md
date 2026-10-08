# Release Changelog ("What's new") API

> Contract for the release notes / What's new popup. Base path `/api/1`.
> Reads are served by **wanderer-query** (8082), writes by **wanderer-command** (8081),
> same split as the rest of the API.
>
> **Versions are app versions.** `version` is always the wanderer-frontend version (the Flutter app,
> Android and web), because that is what travellers install and see. Only the frontend release
> creates drafts. Backend changes travellers notice are written into the app release that ships them
> (or added by an admin in the editor); the backend's own history lives in git and GitHub Releases.

## Enums

| Enum | Values |
|------|--------|
| `Platform` | `ANDROID`, `WEB` |
| `ReleaseStatus` | `DRAFT`, `PUBLISHED` |
| `ReleaseItemType` | `NEW`, `IMPROVED`, `FIXED` |

Enum values are upper case in JSON and in query parameters (`platform=android` is also accepted).

`version` is plain semver `MAJOR.MINOR.PATCH` (e.g. `1.3.0`). No `v` prefix, no pre-release suffix.
Versions are compared numerically (`1.10.0` > `1.9.0`).

## Data model

### `ReleaseDTO`

```json
{
  "id": "6f1c2a8e-1b0e-4a51-9a51-1d7c1f0e2b11",
  "version": "1.3.0",
  "status": "PUBLISHED",
  "headline": "Plan trips faster",
  "showPopup": true,
  "platforms": [
    { "platform": "ANDROID", "releaseDate": "2026-10-10T08:00:00Z" },
    { "platform": "WEB", "releaseDate": null }
  ],
  "items": [
    { "type": "NEW", "title": "One-step trip start", "text": "Start a trip straight from a plan.", "prNumber": 112 },
    { "type": "FIXED", "title": "Single-day plans", "text": "Plans can now start and end on the same day.", "prNumber": 110 },
    { "type": "IMPROVED", "title": "Faster map", "text": "The map loads faster.", "prNumber": null }
  ],
  "createdAt": "2026-10-06T12:00:00Z",
  "updatedAt": "2026-10-06T12:30:00Z"
}
```

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `id` | string (UUID) | no | |
| `version` | string (semver) | no | Unique |
| `status` | `ReleaseStatus` | no | |
| `headline` | string | yes | Max 200 chars. Required to publish |
| `showPopup` | boolean | no | Whether clients show the What's new popup for this release. Default `true` |
| `platforms` | array | no | Platforms this release targets. One entry per platform |
| `platforms[].platform` | `Platform` | no | |
| `platforms[].releaseDate` | string (ISO 8601) | yes | When the release goes live on that platform. `null` = not released there yet |
| `items` | array | no | Ordered. Array order is display order |
| `items[].type` | `ReleaseItemType` | no | |
| `items[].title` | string | no | Max 200 chars |
| `items[].text` | string | yes | Short text, max 500 chars |
| `items[].prNumber` | integer | yes | Source PR number |
| `createdAt` / `updatedAt` | string (ISO 8601) | no | |

### Visibility rule

A release is **visible on platform P** when all hold:

- `status == PUBLISHED`
- `platforms` has an entry for P
- that entry's `releaseDate` is not null and `releaseDate <= now`

Public endpoints only ever return visible releases. Drafts and future-dated releases return 404.

## Public endpoints (wanderer-query, no auth)

### `GET /api/1/releases/{version}?platform={Platform}`

Notes for one version on one platform.

| Status | When |
|--------|------|
| 200 | `ReleaseDTO` |
| 400 | invalid `platform` or `version` |
| 404 | unknown version, or not visible on that platform (draft, platform not targeted, no date yet, date in the future) |

### `GET /api/1/releases?platform={Platform}&page=0&size=20`

Full history for a platform: visible releases only, newest first (by that platform's `releaseDate` desc).
Response is a standard Spring `Page<ReleaseDTO>` (`content`, `totalElements`, `totalPages`, `number`, `size`, ...).

| Status | When |
|--------|------|
| 200 | `Page<ReleaseDTO>` |
| 400 | invalid `platform` |

## Read tracking

The server stores one `lastSeenVersion` per **user** (not per device or platform), so Android, web and
reinstalls share it.

### `GET /api/1/releases/me/unread?platform={Platform}&currentVersion={version}` (wanderer-query, auth)

Unread releases for the current user and the client's installed version.

```json
{
  "lastSeenVersion": "1.2.0",
  "releases": [ /* ReleaseDTO, newest version first */ ]
}
```

`releases` = releases visible on `platform` with `showPopup == true` and
`lastSeenVersion < version <= currentVersion`.

**No last-seen yet** (brand-new user, or first launch after this feature ships): `lastSeenVersion` is
`null` and `releases` is `[]`. The user is treated as having seen everything up to `currentVersion`,
so nobody gets a popup for the version they just installed.

| Status | When |
|--------|------|
| 200 | as above |
| 400 | invalid `platform` or `currentVersion` |
| 401 | no / invalid JWT |

### `PUT /api/1/releases/me/seen` (wanderer-command, auth)

Request:

```json
{ "version": "1.3.0" }
```

Response 200:

```json
{ "lastSeenVersion": "1.3.0" }
```

Monotonic: the stored value only moves forward (`max(stored, version)`). Calling it with an older
version is a no-op and returns the stored value. Idempotent.

| Status | When |
|--------|------|
| 200 | as above |
| 400 | missing / invalid `version` |
| 401 | no / invalid JWT |

### Client flow

On app start (logged-in user):

1. `GET /releases/me/unread?platform=…&currentVersion=<installed>`.
2. If `releases` is non-empty: show the popup (newest first). On dismiss, `PUT /releases/me/seen` with `currentVersion`.
3. If `releases` is empty and `lastSeenVersion` is null: silently `PUT /releases/me/seen` with `currentVersion`.
   This sets the baseline for new users so the *next* release does show. Users with a last-seen version
   keep it, so notes published after the update still pop up.

Any later client (other platform, reinstall) then gets an empty list for versions already seen.

## Admin endpoints (ADMIN role)

`401` without JWT, `403` for non-admins on all of these.

### `GET /api/1/admin/releases?page=0&size=20` (wanderer-query)

All releases (drafts and published), newest first by `createdAt`. `Page<ReleaseDTO>`.

### `GET /api/1/admin/releases/{version}` (wanderer-query)

One release in any state. `200 ReleaseDTO`, `404` if unknown.

### `PUT /api/1/admin/releases/{version}` (wanderer-command)

Full replace of the editable fields. Allowed for `DRAFT` and `PUBLISHED` (so you can set a
platform's `releaseDate` after publishing, e.g. a later Android rollout). An unknown version is
created as a `DRAFT`, so admins can write notes for a version CI hasn't drafted.

```json
{
  "headline": "Plan trips faster",
  "showPopup": true,
  "platforms": [
    { "platform": "ANDROID", "releaseDate": "2026-10-10T08:00:00Z" },
    { "platform": "WEB", "releaseDate": null }
  ],
  "items": [
    { "type": "NEW", "title": "One-step trip start", "text": "Start a trip straight from a plan.", "prNumber": 112 }
  ]
}
```

- `items` order = display order (reorder by sending the array in the new order).
- `platforms` must not repeat a platform.

| Status | When |
|--------|------|
| 200 | saved `ReleaseDTO` (created if the version was unknown) |
| 400 | validation error (blank title, too long, duplicate platform, ...) |

### `POST /api/1/admin/releases/{version}/publish` (wanderer-command)

Sets `status = PUBLISHED`. Idempotent. Any platform with `releaseDate: null` gets the publish time, so
it goes live immediately; dates already set (past or future) are kept. To schedule a platform for later,
set its date via `PUT` before publishing.

| Status | When |
|--------|------|
| 200 | published `ReleaseDTO` |
| 400 | `headline` blank or no platforms |
| 404 | unknown version |

## CI draft creation (wanderer-command, CI token)

### `POST /api/1/releases/drafts`

Called by CI when a release is tagged. Not JWT-protected; requires header
`X-Release-Token: <secret>` matching the server's `release.ci.token` (env `RELEASE_CI_TOKEN`).
If the server has no token configured, every call is rejected.

```json
{
  "version": "1.3.0",
  "platforms": ["ANDROID", "WEB"],
  "prs": [
    { "number": 112, "title": "One-step trip start", "label": "feature", "summary": "Start a trip straight from a plan." },
    { "number": 110, "title": "Allow single-day plans", "label": "bug", "summary": "Plans can now start and end on the same day." }
  ]
}
```

- `platforms` optional; on create defaults to `["ANDROID", "WEB"]` (with `releaseDate: null`); ignored on update.
- `label` → item type, case-insensitive: `new`/`feature`/`feat` → `NEW`; `fixed`/`fix`/`bug`/`bugfix` → `FIXED`;
  anything else → `IMPROVED`.
- Item `title` = PR title, `text` = `summary`, `prNumber` = `number`.
- **Upsert, never duplicates.** No release for `version` → create a `DRAFT` (`headline: null`, `showPopup: true`).
  Existing draft → for each PR, the item with the same `prNumber` is overwritten, new PRs are appended;
  manually added items and other PR items are kept.

| Status | When |
|--------|------|
| 201 | draft created, `ReleaseDTO` |
| 200 | existing draft updated, `ReleaseDTO` |
| 400 | invalid version / body |
| 401 | missing or wrong `X-Release-Token` (or no token configured) |
| 409 | that version is already `PUBLISHED` |

### `POST /api/1/releases/publish`

Called by the frontend release pipeline once the release is deployed, with notes written for
travellers. Same `X-Release-Token` header. Creates the release if needed (or reuses the CI draft),
replaces `headline` and `items`, sets `showPopup: true`, keeps existing platforms (default
`["ANDROID", "WEB"]`) and publishes, so platforms without a date go live now.

```json
{
  "version": "2.1.0",
  "headline": "A simpler way to start your trips",
  "items": [
    { "type": "NEW", "title": "Save trips for later", "text": "Not ready to go? Save your trip as a plan and come back to it whenever you're ready." }
  ]
}
```

| Status | When |
|--------|------|
| 200 | published `ReleaseDTO` |
| 400 | validation error (invalid version, blank headline, no items, ...) |
| 401 | missing or wrong token |
| 409 | version already published (a rerun never overwrites live notes; edit them in the admin editor) |

### Configuration

| Where | Setting |
|-------|---------|
| wanderer-command property | `release.ci.token` (env `RELEASE_CI_TOKEN`), empty by default |
| Helm (wanderer-command) | `application.release.ciToken` |
| GitHub Actions | repository/environment secret `RELEASE_CI_TOKEN`, passed by `helm-deploy.yml` |

The CI job that calls the endpoint needs the same value (e.g. the same `RELEASE_CI_TOKEN` secret):

```bash
curl -fsS -X POST "$WANDERER_COMMAND_URL/api/1/releases/drafts" \
  -H "Content-Type: application/json" \
  -H "X-Release-Token: $RELEASE_CI_TOKEN" \
  -d @release-draft.json
```

## Errors

Error bodies are plain text messages (existing API convention); 401/403/404 have empty bodies.

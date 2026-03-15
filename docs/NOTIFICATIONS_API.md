# Notifications API — Frontend Integration Guide

> **Version:** 0.10.0-SNAPSHOT · **Date:** March 14, 2026

---

## Overview

The backend now supports **persisted in-app notifications**. Previously, events were only broadcast transiently via WebSocket — if the client was disconnected, events were lost. Now every relevant event creates a `Notification` record in the database that the frontend can query at any time.

Notifications **complement** (not replace) the existing WebSocket real-time events. The recommended pattern is:

1. **On app load** → fetch unread count + first page of notifications via REST.
2. **While connected** → listen to WebSocket events for real-time updates and increment the local unread badge.
3. **On reconnect / pull-to-refresh** → re-fetch from REST to reconcile any missed events.

---

## Data Model

### `NotificationDTO`

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "recipientId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "actorId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "type": "COMMENT_ON_TRIP",
  "referenceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "alice commented on your trip \"Camino de Santiago\"",
  "read": false,
  "createdAt": "2026-03-14T10:30:00Z"
}
```

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `id` | `string (UUID)` | No | Unique notification ID |
| `recipientId` | `string (UUID)` | No | The user this notification is for |
| `actorId` | `string (UUID)` | Yes | The user who triggered the event (`null` for system events like achievements) |
| `type` | `string (enum)` | No | Notification type — see table below |
| `referenceId` | `string (UUID)` | Yes | Contextual ID for navigation (trip, comment, friend request, user, or achievement) |
| `message` | `string` | No | Human-readable message, ready to display as-is |
| `read` | `boolean` | No | Whether the user has marked this notification as read |
| `createdAt` | `string (ISO 8601)` | No | Timestamp of when the notification was created |

### Notification Types

| `type` value | `actorId` | `referenceId` points to | Trigger | Suggested Icon |
|---|---|---|---|---|
| `FRIEND_REQUEST_RECEIVED` | Sender | Friend request ID | Someone sent you a friend request | 👤➕ |
| `FRIEND_REQUEST_ACCEPTED` | Accepter | Friend request ID | Your friend request was accepted | 🤝 |
| `FRIEND_REQUEST_DECLINED` | Decliner | Friend request ID | Your friend request was declined | 👤✖ |
| `COMMENT_ON_TRIP` | Commenter | Trip ID | Someone commented on your trip | 💬 |
| `REPLY_TO_COMMENT` | Replier | Parent comment ID | Someone replied to your comment | ↩️💬 |
| `COMMENT_REACTION` | Reactor | Comment ID | Someone reacted to your comment | ❤️ |
| `NEW_FOLLOWER` | Follower | Follower's user ID | Someone started following you | 👤➕ |
| `ACHIEVEMENT_UNLOCKED` | `null` | Achievement ID | You unlocked an achievement | 🏆 |
| `TRIP_STATUS_CHANGED` | Trip owner | Trip ID | A user you follow started/finished a trip | 🥾 |
| `TRIP_UPDATE_POSTED` | Trip owner | Trip ID | A user you follow posted an update with a message | 📍 |

> **Note:** The backend suppresses self-notifications. You will never receive a notification where `actorId` equals the authenticated user's ID.

---

## REST Endpoints

All notification endpoints require authentication (`Authorization: Bearer <token>`).

### 1. Get My Notifications (paginated)

```
GET /api/1/notifications/me
Host: wanderer-query (port 8082)
Authorization: Bearer <token>
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | `int` | `0` | Page number (0-indexed) |
| `size` | `int` | `20` | Items per page |
| `sort` | `string` | `createdAt,desc` | Sort field and direction |

**Response:** `200 OK`

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "recipientId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "actorId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "type": "FRIEND_REQUEST_RECEIVED",
      "referenceId": "d4e5f6a7-b8c9-0123-4567-890abcdef012",
      "message": "alice sent you a friend request",
      "read": false,
      "createdAt": "2026-03-14T10:30:00Z"
    },
    {
      "id": "661f9511-f3ac-52e5-b827-557766551111",
      "recipientId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "actorId": null,
      "type": "ACHIEVEMENT_UNLOCKED",
      "referenceId": "aaaa1111-bbbb-cccc-dddd-eeee22223333",
      "message": "You unlocked the achievement \"First Century\"!",
      "read": true,
      "createdAt": "2026-03-13T15:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false }
  },
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "empty": false
}
```

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/1/notifications/me?page=0&size=10"
```

---

### 2. Get Unread Count

```
GET /api/1/notifications/me/unread-count
Host: wanderer-query (port 8082)
Authorization: Bearer <token>
```

**Response:** `200 OK`

```json
7
```

Returns a plain `long` value — the number of unread notifications.

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/1/notifications/me/unread-count"
```

---

### 3. Mark a Single Notification as Read

```
PATCH /api/1/notifications/{id}/read
Host: wanderer-command (port 8081)
Authorization: Bearer <token>
```

**Path Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | The notification ID to mark as read |

**Response:** `202 Accepted` (empty body)

**Error Responses:**

| Status | Reason |
|--------|--------|
| `404 Not Found` | Notification doesn't exist |
| `403 Forbidden` | The authenticated user is not the recipient |

**Example:**
```bash
curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/api/1/notifications/550e8400-e29b-41d4-a716-446655440000/read"
```

---

### 4. Mark All Notifications as Read

```
PATCH /api/1/notifications/me/read-all
Host: wanderer-command (port 8081)
Authorization: Bearer <token>
```

**Response:** `202 Accepted`

```json
12
```

Returns the count of notifications that were marked as read (as `int`). Returns `0` if there were no unread notifications.

**Example:**
```bash
curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/api/1/notifications/me/read-all"
```

---

## Endpoint Summary Table

| Method | Endpoint | Service (Port) | Auth | Description |
|--------|----------|----------------|------|-------------|
| `GET` | `/api/1/notifications/me` | query (8082) | 🔒 | Paginated notifications list |
| `GET` | `/api/1/notifications/me/unread-count` | query (8082) | 🔒 | Unread badge count |
| `PATCH` | `/api/1/notifications/{id}/read` | command (8081) | 🔒 | Mark one as read |
| `PATCH` | `/api/1/notifications/me/read-all` | command (8081) | 🔒 | Mark all as read |

---

## Frontend Implementation Recommendations

### Notification Badge (Unread Count)

```
App Load → GET /notifications/me/unread-count → display badge on bell icon
```

- Poll every **30–60 seconds** as a fallback, or use the existing WebSocket connection to increment the count locally on incoming events.
- After calling `PATCH /{id}/read` or `PATCH /me/read-all`, update the badge **optimistically** (decrement or reset to 0), then optionally re-fetch to confirm.

### Notification List / Dropdown

```
User taps bell icon → GET /notifications/me?page=0&size=20
```

- Render each notification using the `message` field directly — it is pre-formatted by the backend.
- Use `type` to choose an icon and determine the tap/click navigation target.
- Use `read` to apply visual distinction (bold text, colored dot, background highlight, etc.).
- Support infinite scroll by incrementing `page` as the user scrolls down.

### Navigation on Tap/Click

Use `type` + `referenceId` to deep-link to the relevant screen:

| `type` | Navigate to |
|--------|-------------|
| `FRIEND_REQUEST_RECEIVED` | Friend requests screen |
| `FRIEND_REQUEST_ACCEPTED` | User profile (`actorId`) |
| `FRIEND_REQUEST_DECLINED` | Friend requests screen _(optional)_ |
| `COMMENT_ON_TRIP` | Trip detail (`referenceId` = trip ID) → comments section |
| `REPLY_TO_COMMENT` | Trip detail → specific comment thread (`referenceId` = parent comment ID) |
| `COMMENT_REACTION` | Trip detail → specific comment (`referenceId` = comment ID) |
| `NEW_FOLLOWER` | User profile (`referenceId` = follower's user ID) |
| `ACHIEVEMENT_UNLOCKED` | Achievements screen (`referenceId` = achievement ID) |
| `TRIP_STATUS_CHANGED` | Trip detail (`referenceId` = trip ID) |
| `TRIP_UPDATE_POSTED` | Trip detail (`referenceId` = trip ID) → updates section |

### Mark-as-Read Strategy

| Strategy | How | Best for |
|----------|-----|----------|
| **Mark on view** | Call `PATCH /me/read-all` when the notification list is opened | Simplest UX |
| **Mark on tap** | Call `PATCH /{id}/read` when a specific notification is tapped | Granular control |
| **Hybrid** | Mark individual on tap + add a "Mark all as read" button | Best of both |

---

## TypeScript Types

```typescript
type NotificationType =
  | 'FRIEND_REQUEST_RECEIVED'
  | 'FRIEND_REQUEST_ACCEPTED'
  | 'FRIEND_REQUEST_DECLINED'
  | 'COMMENT_ON_TRIP'
  | 'REPLY_TO_COMMENT'
  | 'COMMENT_REACTION'
  | 'NEW_FOLLOWER'
  | 'ACHIEVEMENT_UNLOCKED'
  | 'TRIP_STATUS_CHANGED'
  | 'TRIP_UPDATE_POSTED';

interface NotificationDTO {
  id: string;
  recipientId: string;
  actorId: string | null;
  type: NotificationType;
  referenceId: string | null;
  message: string;
  read: boolean;
  createdAt: string; // ISO 8601
}

interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // current page (0-indexed)
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

type NotificationsPage = PagedResponse<NotificationDTO>;
```

---

## Kotlin Types (Android)

```kotlin
enum class NotificationType {
    FRIEND_REQUEST_RECEIVED,
    FRIEND_REQUEST_ACCEPTED,
    FRIEND_REQUEST_DECLINED,
    COMMENT_ON_TRIP,
    REPLY_TO_COMMENT,
    COMMENT_REACTION,
    NEW_FOLLOWER,
    ACHIEVEMENT_UNLOCKED,
    TRIP_STATUS_CHANGED,
    TRIP_UPDATE_POSTED
}

data class NotificationDTO(
    val id: String,
    val recipientId: String,
    val actorId: String?,
    val type: NotificationType,
    val referenceId: String?,
    val message: String,
    val read: Boolean,
    val createdAt: Instant
)
```

---

## FAQ

**Q: Are WebSocket events still broadcast?**
Yes. The existing `Broadcastable` event system on `/topic/trips/{tripId}` and `/topic/users/{userId}` is unchanged. Notifications are a parallel persistence layer, not a replacement.

**Q: Can a notification be deleted?**
Not in 0.10.0-SNAPSHOT. Notifications can only be marked as read. Delete/archive support may be added in a future version.

**Q: Is there a limit on stored notifications?**
Not currently enforced. A future version may add TTL-based cleanup (e.g., auto-delete after 90 days).

**Q: What about push notifications (Firebase / APNs)?**
Out of scope for 0.10.0-SNAPSHOT. This in-app notification system provides the data foundation — push delivery can be added as a consumer of the same event listener in a future iteration.

**Q: Will I get notified about my own actions?**
No. The backend suppresses self-notifications (commenting on your own trip, reacting to your own comment, etc.).

**Q: What happens to fan-out notifications (trip status changes)?**
When a user starts or finishes a trip, all their followers and friends receive a notification. These are batch-inserted. If a user has many followers, there may be a brief delay before all notifications are persisted.

---

## Backend Classes Reference

All classes introduced in this PR, grouped by module and package.

### Commons Module (`commons/`)

| Package | Class | Type | Description |
|---------|-------|------|-------------|
| `domain` | `Notification` | JPA Entity | Persisted notification record with `id`, `recipientId`, `actorId`, `type`, `referenceId`, `message`, `read`, and `createdAt` fields. Mapped to the `notifications` table. |
| `domain` | `NotificationType` | Enum | 10 notification type constants: `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_REQUEST_DECLINED`, `COMMENT_ON_TRIP`, `REPLY_TO_COMMENT`, `COMMENT_REACTION`, `NEW_FOLLOWER`, `ACHIEVEMENT_UNLOCKED`, `TRIP_STATUS_CHANGED`, `TRIP_UPDATE_POSTED`. |
| `dto` | `NotificationDTO` | Record | Immutable DTO returned by query endpoints. All UUID fields serialized as strings. |
| `mapper` | `NotificationMapper` | MapStruct Interface | Converts `Notification` entity → `NotificationDTO`. UUID and enum fields mapped via expressions. |
| `constants` | `ApiConstants` | Updated | Added `NOTIFICATIONS_PATH`, `NOTIFICATIONS_ME_ENDPOINT`, `NOTIFICATIONS_UNREAD_COUNT_ENDPOINT`, `NOTIFICATION_READ_ENDPOINT`, and `NOTIFICATIONS_READ_ALL_ENDPOINT`. |

### Command Module (`wanderer-command/`)

| Package | Class | Type | Description |
|---------|-------|------|-------------|
| `repository` | `NotificationRepository` | JPA Repository | Extends `JpaRepository<Notification, UUID>`. Provides `markAllAsReadByRecipientId(UUID)` via a custom `@Query` update. |
| `service` | `NotificationService` | Interface | Defines `markAsRead(UUID userId, UUID notificationId)` and `markAllAsRead(UUID userId)`. |
| `service.impl` | `NotificationServiceImpl` | Service | Implements mark-as-read logic with ownership validation (`AccessDeniedException` if the user is not the recipient). |
| `controller` | `NotificationController` | REST Controller | Exposes `PATCH /{id}/read` and `PATCH /me/read-all` under `/api/1/notifications`. Secured with `@PreAuthorize`. |
| `handler` | `NotificationEventListener` | Event Listener | Core component — listens to 9 domain events (`FriendRequestSentEvent`, `FriendRequestAcceptedEvent`, `FriendRequestDeclinedEvent`, `CommentAddedEvent`, `CommentReactionEvent`, `UserFollowedEvent`, `AchievementUnlockedEvent`, `TripStatusChangedEvent`, `TripUpdatedEvent`) and creates `Notification` records. Suppresses self-notifications. Fan-out for trip events queries `UserFollowRepository` + `FriendshipRepository` and batch-inserts. |

### Query Module (`wanderer-query/`)

| Package | Class | Type | Description |
|---------|-------|------|-------------|
| `repository` | `NotificationRepository` | JPA Repository | Extends `JpaRepository<Notification, UUID>`. Provides `findByRecipientIdOrderByCreatedAtDesc(UUID, Pageable)` and `countByRecipientIdAndReadFalse(UUID)`. |
| `service` | `NotificationQueryService` | Interface | Defines `getNotifications(UUID, Pageable)` and `getUnreadCount(UUID)`. |
| `service.impl` | `NotificationQueryServiceImpl` | Service | Delegates to repository and maps entities to DTOs via `NotificationMapper`. |
| `controller` | `NotificationQueryController` | REST Controller | Exposes `GET /me` (paginated, default 20 per page) and `GET /me/unread-count` under `/api/1/notifications`. Secured with `@PreAuthorize`. |

### Database Migration

| File | Description |
|------|-------------|
| `db/changelog/033-create-notifications-table.yaml` | Creates the `notifications` table with columns matching the `Notification` entity. Adds a FK to `users` on `recipient_id` with `ON DELETE CASCADE`, and a composite index on `(recipient_id, is_read, created_at DESC)` for efficient querying. |
| `db/changelog/db.changelog-master.yaml` | Updated to include migration `033`. |

### Tests

| Module | Class | Tests | Coverage |
|--------|-------|-------|----------|
| `wanderer-command` | `NotificationServiceImplTest` | 5 | `markAsRead` success / not found / access denied, `markAllAsRead` success / zero results |
| `wanderer-command` | `NotificationEventListenerTest` | 13 | All 9 event types, self-notification suppression (comment on own trip, self-reaction), reply notifies parent author, fan-out for trip status/updates, skips non-major status changes, skips system update types |
| `wanderer-command` | `NotificationControllerTest` | 2 | `PATCH /{id}/read` and `PATCH /me/read-all` endpoint wiring |
| `wanderer-query` | `NotificationQueryServiceImplTest` | 4 | Paginated results, empty page, unread count, unread count zero |


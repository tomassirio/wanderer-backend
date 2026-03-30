<p align="center">
  <img src="https://raw.githubusercontent.com/tomassirio/wanderer-backend/main/wanderer-auth/src/main/resources/assets/wanderer-logo.png" alt="Wanderer Logo" width="400" />
</p>

<p align="center">
  <strong>Real-time pilgrimage tracking — Utrecht to Santiago de Compostela</strong>
</p>

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen?logo=spring&logoColor=white)
![Version](https://img.shields.io/badge/version-1.1.1-blue)
![Build Status](https://img.shields.io/github/actions/workflow/status/tomassirio/wanderer-backend/merge.yml?branch=main&label=build)
![License](https://img.shields.io/badge/license-MIT-green)
![Coverage](https://img.shields.io/badge/coverage-51%25-orange)

![PostgresSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/docker-enabled-2496ED?logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/kubernetes-ready-326CE5?logo=kubernetes&logoColor=white)
![Architecture](https://img.shields.io/badge/architecture-CQRS-blueviolet)

Wanderer is the backend for a real-time pilgrimage tracking platform built for my walk from Utrecht to Santiago de Compostela — roughly 48 days and 50 km per day. It lets friends, family, and anyone following along see where I am, read updates, leave comments, and watch achievements unlock as the journey unfolds.

The system is built on a CQRS (Command Query Responsibility Segregation) architecture with three independently deployable Spring Boot services backed by PostgreSQL, connected to a companion frontend through REST APIs and WebSockets.

## Table of Contents

- [Architecture](#-architecture)
- [Features](#-features)
- [Getting Started](#-getting-started)
- [API Overview](#-api-overview)
- [Real-Time Events](#-real-time-events)
- [Data Model](#-data-model)
- [Security](#-security)
- [Deployment](#-deployment)
- [Documentation](#-documentation)
- [Contributing](#-contributing)
- [License](#-license)

## 🏗️ Architecture

The backend is a multi-module Maven project split by responsibility:

```
wanderer-backend/
├── commons/            Shared domain entities, DTOs, mappers, and constants
├── wanderer-auth/      Authentication & authorization          → Port 8083
├── wanderer-command/   Write operations (create, update, delete) → Port 8081
├── wanderer-query/     Read operations (queries & lists)        → Port 8082
├── docs/               Additional guides and documentation
└── docker-compose.yml  Full local stack with two Postgres instances
```

| Layer | Responsibility |
|-------|---------------|
| **commons** | Domain entities, DTOs, MapStruct mappers, ApiConstants, enums, exceptions |
| **wanderer-auth** | Registration, login, JWT issuance, refresh tokens, password reset, email verification, admin role management |
| **wanderer-command** | Trip CRUD, trip updates (location posts), comments, reactions, friend requests, follows, admin maintenance, WebSocket event broadcasting |
| **wanderer-query** | Trip queries, user lookups, comment retrieval, achievement listings, promoted trips, friendship/follow queries, admin statistics |

### Technology Stack

| Category | Technology |
|----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Database | PostgreSQL 16 with Liquibase migrations |
| Security | JWT (access + refresh tokens), bcrypt, role-based authorization |
| Real-Time | WebSocket (native Spring WebSocket) |
| DTO Mapping | MapStruct |
| External APIs | Google Maps (polylines, geocoding, weather) |
| Email | SMTP (configurable, Brevo/Sendinblue by default) |
| Code Style | Spotless with Google Java Format (AOSP) |
| Testing | JUnit 5, Cucumber (BDD), JaCoCo (coverage) |
| Containers | Docker via Jib Maven Plugin |
| Orchestration | Kubernetes + Helm |
| API Docs | SpringDoc OpenAPI (Swagger UI) |

## ✨ Features

### Trip Tracking
- Create trips with configurable visibility (Public, Private, Protected) and modality (Simple or Multi-Day)
- Post location updates with GPS coordinates, altitude, battery level, and messages
- Automatic polyline generation from location history
- Reverse geocoding of coordinates into city and country
- Day-by-day tracking for multi-day trips with start/end day toggling
- Trip status lifecycle: Created → In Progress → Paused / Resting → Finished

### Social
- Friend requests with accept/decline workflow
- Bidirectional friendships that unlock access to Protected trips
- User follows for one-directional social connections
- Public trip discovery and promoted/featured trips

### Comments & Reactions
- Threaded comments on trips with one level of nesting (replies)
- Five reaction types on comments: ❤️ Heart, 😊 Smiley, 😢 Sad, 😂 Laugh, 😠 Anger

### Achievements
- 25 predefined achievements across categories: distance milestones, update counts, trip duration, and social milestones (followers, friends)
- Automatic unlock when thresholds are reached during trip updates
- Per-user and per-trip achievement queries

### Real-Time Updates
- WebSocket endpoint at `/ws` with 24 event types
- Live broadcasting to topic channels (`/topic/trips/{tripId}`, `/topic/users/{userId}`)
- Events for trip changes, comments, reactions, friend requests, follows, achievements, and polyline updates

### Administration
- Admin role promotion and demotion
- Bootstrap admin mechanism for initial setup
- Trip polyline and geocoding recomputation
- Trip promotion with donation link support
- Trip maintenance statistics dashboard
- User and credential management

### Weather
- Google Weather API integration for live weather conditions at the current location

### Email
- Email verification on registration
- Password reset with time-limited, one-time-use tokens
- Configurable SMTP provider

## 🚀 Getting Started

### Prerequisites

- **Java 21** — required
- **Maven 3.6+** — required
- **Docker** — optional, for running the full stack locally
- **PostgreSQL 16** — required if running without Docker

### Build

```bash
# Build all modules
mvn clean install

# Build a single module
mvn clean install -pl wanderer-command

# Format code (run before committing)
mvn spotless:apply

# Run tests with coverage
mvn clean verify
```

### Run Locally

Start each service individually:

```bash
# Auth (Port 8083)
mvn spring-boot:run -pl wanderer-auth

# Command (Port 8081)
mvn spring-boot:run -pl wanderer-command

# Query (Port 8082)
mvn spring-boot:run -pl wanderer-query
```

Or bring up the full stack with Docker Compose:

```bash
docker-compose up
```

This starts two PostgreSQL databases (one for auth, one for command/query), and all three services with sensible defaults.

### Swagger UI

Once running, interactive API docs are available at:

- Auth: http://localhost:8083/swagger-ui.html
- Command: http://localhost:8081/swagger-ui.html
- Query: http://localhost:8082/swagger-ui.html

## 🌐 API Overview

All endpoints are under `/api/1`. Below is a summary grouped by domain. For detailed request/response examples, see the [Wiki](https://github.com/tomassirio/wanderer-backend/wiki).

### Authentication — `wanderer-auth` · Port 8083

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/auth/register` | Register a new user (triggers email verification) |
| POST | `/api/1/auth/verify-email` | Verify email with token |
| POST | `/api/1/auth/login` | Login, returns access and refresh tokens |
| POST | `/api/1/auth/logout` | Logout, blacklists tokens 🔒 |
| POST | `/api/1/auth/refresh` | Exchange refresh token for new token pair |
| POST | `/api/1/auth/password/reset` | Request a password reset email |
| POST | `/api/1/auth/password/reset-form` | Complete password reset with token |
| PUT | `/api/1/auth/password/change` | Change password (authenticated) 🔒 |

### Users — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/users` | Create user |
| PATCH | `/api/1/users/me` | Update own profile 🔒 |
| DELETE | `/api/1/users/me` | Delete own account 🔒 |

### Users — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/users/{id}` | Get user by ID |
| GET | `/api/1/users/username/{username}` | Get user by username |
| GET | `/api/1/users/me` | Get current user profile 🔒 |
| GET | `/api/1/users` | List all users with stats (admin) 🔒 |

### Friends & Follows — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/users/friends/requests` | Send friend request 🔒 |
| POST | `/api/1/users/friends/requests/{id}/accept` | Accept friend request 🔒 |
| DELETE | `/api/1/users/friends/requests/{id}` | Decline/cancel friend request 🔒 |
| DELETE | `/api/1/users/friends/{friendId}` | Remove friendship 🔒 |
| POST | `/api/1/users/follows` | Follow a user 🔒 |
| DELETE | `/api/1/users/follows/{followedId}` | Unfollow a user 🔒 |

### Friends & Follows — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/users/me/friends` | Get my friends 🔒 |
| GET | `/api/1/users/{userId}/friends` | Get user's friends |
| GET | `/api/1/users/friends/requests/received` | Received friend requests 🔒 |
| GET | `/api/1/users/friends/requests/sent` | Sent friend requests 🔒 |
| GET | `/api/1/users/me/following` | Users I follow 🔒 |
| GET | `/api/1/users/me/followers` | My followers 🔒 |
| GET | `/api/1/users/{userId}/following` | Users a given user follows |
| GET | `/api/1/users/{userId}/followers` | Followers of a given user |

### Trips — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/trips` | Create trip 🔒 |
| POST | `/api/1/trips/from-plan/{tripPlanId}` | Create trip from plan 🔒 |
| PUT | `/api/1/trips/{id}` | Update trip 🔒 |
| PATCH | `/api/1/trips/{id}/visibility` | Change visibility 🔒 |
| PATCH | `/api/1/trips/{id}/status` | Change status 🔒 |
| PATCH | `/api/1/trips/{id}/settings` | Update settings 🔒 |
| PATCH | `/api/1/trips/{id}/toggle-day` | Toggle day (multi-day trips) 🔒 |
| DELETE | `/api/1/trips/{id}` | Delete trip 🔒 |

### Trips — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/trips/{id}` | Get trip by ID |
| GET | `/api/1/trips/me` | Get my trips 🔒 |
| GET | `/api/1/trips/me/available` | Get trips available to me 🔒 |
| GET | `/api/1/trips/users/{userId}` | Get trips by user ID |
| GET | `/api/1/trips/public` | Get public trips |
| GET | `/api/1/trips` | List all trips (admin) 🔒 |

### Trip Plans — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/trips/plans` | Create trip plan 🔒 |
| PUT | `/api/1/trips/plans/{planId}` | Update trip plan 🔒 |
| DELETE | `/api/1/trips/plans/{planId}` | Delete trip plan 🔒 |

### Trip Plans — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/trips/plans/{planId}` | Get trip plan |
| GET | `/api/1/trips/plans/me` | Get my trip plans 🔒 |

### Trip Updates — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/trips/{tripId}/updates` | Post a location update 🔒 |

### Trip Updates — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/trips/{tripId}/updates` | Get updates for a trip |
| GET | `/api/1/trips/updates/{id}` | Get a single update by ID |

### Comments & Reactions — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/trips/{tripId}/comments` | Create comment or reply 🔒 |
| POST | `/api/1/comments/{commentId}/reactions` | Add reaction to comment 🔒 |
| DELETE | `/api/1/comments/{commentId}/reactions` | Remove reaction 🔒 |

### Comments — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/trips/{tripId}/comments` | Get comments for a trip |
| GET | `/api/1/comments/{id}` | Get comment by ID |

### Achievements — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/achievements` | List all achievements |
| GET | `/api/1/users/me/achievements` | Get my achievements 🔒 |
| GET | `/api/1/users/{userId}/achievements` | Get user's achievements |
| GET | `/api/1/trips/{tripId}/achievements` | Get trip's achievements |

### Promoted Trips — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/promoted-trips` | Get featured/promoted trips |

### Admin — `wanderer-command` · Port 8081

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/1/admin/users/{userId}/promote` | Promote user to admin 🔒 |
| DELETE | `/api/1/admin/users/{userId}/promote` | Demote admin 🔒 |
| DELETE | `/api/1/admin/users/{userId}` | Delete user 🔒 |
| POST | `/api/1/admin/trips/{tripId}/recompute-polyline` | Recompute polyline 🔒 |
| POST | `/api/1/admin/trips/{tripId}/recompute-geocoding` | Recompute geocoding 🔒 |
| POST | `/api/1/admin/trips/{tripId}/promote` | Promote trip 🔒 |
| PUT | `/api/1/admin/trips/{tripId}/promote` | Update promotion / donation link 🔒 |
| DELETE | `/api/1/admin/trips/{tripId}/promote` | Unpromote trip 🔒 |
| POST | `/api/1/admin/trip-plans/{tripPlanId}/recompute-polyline` | Recompute plan polyline 🔒 |

### Admin — `wanderer-query` · Port 8082

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/1/admin/trips/stats` | Trip maintenance statistics 🔒 |

> 🔒 = Requires authentication (JWT Bearer token). Admin endpoints also require the `ADMIN` role.

## 📡 Real-Time Events

The command service exposes a WebSocket endpoint at `/ws`. Clients subscribe to topic channels and receive JSON messages as events occur.

| Channel | Events |
|---------|--------|
| `/topic/trips/{tripId}` | `TRIP_UPDATED`, `TRIP_STATUS_CHANGED`, `TRIP_VISIBILITY_CHANGED`, `TRIP_METADATA_UPDATED`, `TRIP_SETTINGS_UPDATED`, `TRIP_DELETED`, `COMMENT_ADDED`, `COMMENT_REACTION_ADDED`, `COMMENT_REACTION_REMOVED`, `COMMENT_REACTION_REPLACED`, `ACHIEVEMENT_UNLOCKED`, `POLYLINE_UPDATED` |
| `/topic/users/{userId}` | `TRIP_CREATED`, `TRIP_PLAN_CREATED`, `TRIP_PLAN_UPDATED`, `TRIP_PLAN_DELETED`, `FRIEND_REQUEST_SENT`, `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_REQUEST_DECLINED`, `FRIEND_REQUEST_CANCELLED`, `USER_FOLLOWED`, `USER_UNFOLLOWED` |

## 🗄️ Data Model

### Core Entities

| Entity | Key Fields | Description |
|--------|-----------|-------------|
| **User** | `id` (UUID), `username`, `userDetails` (display name, bio, avatar) | An authenticated user |
| **Trip** | `id`, `name`, `userId`, `tripSettings`, `tripDetails`, `encodedPolyline` | A walking journey with settings, details, and location history |
| **TripDay** | `id`, `tripId`, `dayNumber`, `startTimestamp`, `endTimestamp` | A single day within a multi-day trip |
| **TripUpdate** | `id`, `tripId`, `location` (JSONB), `battery`, `message`, `city`, `country`, `updateType` | A location/status post during a trip |
| **TripPlan** | `id`, `name`, `planType`, `startLocation`, `endLocation`, `metadata` (JSONB) | A route plan that can be turned into a trip |
| **Comment** | `id`, `tripId`, `userId`, `content`, `parentCommentId`, `replies` | A comment on a trip, with optional nested replies |
| **CommentReaction** | `id`, `commentId`, `userId`, `reactionType` | A user's reaction on a comment |
| **Achievement** | `id`, `type`, `name`, `description`, `thresholdValue` | A system-defined achievement template |
| **UserAchievement** | `id`, `userId`, `achievementId`, `tripId`, `unlockedAt` | A user's earned achievement |
| **Friendship** | `id`, `userId`, `friendId` | A confirmed bidirectional friend relationship |
| **FriendRequest** | `id`, `senderId`, `receiverId`, `status` | A pending, accepted, or declined friend request |
| **UserFollow** | `id`, `followerId`, `followedId` | A one-directional follow relationship |
| **PromotedTrip** | `id`, `tripId`, `donationLink`, `isPreAnnounced`, `countdownStartDate` | A featured trip shown in discovery |

### Enums

| Enum | Values |
|------|--------|
| TripVisibility | `PUBLIC`, `PRIVATE`, `PROTECTED` |
| TripStatus | `CREATED`, `IN_PROGRESS`, `PAUSED`, `RESTING`, `FINISHED` |
| TripModality | `SIMPLE`, `MULTI_DAY` |
| TripPlanType | `SIMPLE`, `MULTI_DAY` |
| UpdateType | `REGULAR`, `DAY_START`, `DAY_END`, `TRIP_STARTED`, `TRIP_ENDED` |
| ReactionType | `HEART`, `SMILEY`, `SAD`, `LAUGH`, `ANGER` |
| FriendRequestStatus | `PENDING`, `ACCEPTED`, `DECLINED` |

## 🔒 Security

### Authentication Flow

1. **Register** → email verification link sent → **verify email** → account activated
2. **Login** → returns a short-lived access token (15 min default) and a long-lived refresh token (7 days)
3. **Refresh** → exchange a valid refresh token for a new token pair (rotation policy — old refresh token is revoked)
4. **Logout** → access token blacklisted by JTI, all refresh tokens revoked

### Authorization

- Role-based access: `USER` and `ADMIN` roles enforced with Spring Security `@PreAuthorize`
- Protected trips visible only to friends of the owner
- Public endpoints: registration, login, token refresh, password reset, public trips, user profiles
- Admin endpoints: user promotion, trip maintenance, statistics

### Token Storage

All tokens (refresh, password reset, blacklist) are hashed with SHA-256 before being stored in the database. Expired tokens are cleaned up automatically.

## 🐳 Deployment

### Docker Compose

The included `docker-compose.yml` runs the full stack:

- **postgres-cqrs** — PostgreSQL for command and query services (port 5432)
- **postgres-auth** — PostgreSQL for the auth service (port 5433)
- **wanderer-command** — Command service (port 8081)
- **wanderer-query** — Query service (port 8082)
- **wanderer-auth** — Auth service (port 8083)

```bash
docker-compose up
```

### Building Docker Images

```bash
mvn clean compile jib:dockerBuild -pl wanderer-command
mvn clean compile jib:dockerBuild -pl wanderer-query
mvn clean compile jib:dockerBuild -pl wanderer-auth
```

Images are also published to `ghcr.io/tomassirio/` via CI.

### Kubernetes

The project is designed for Kubernetes deployment with Helm charts covering services, ConfigMaps, Secrets, Ingress, and health probes.

## 📚 Documentation

| Resource | Description |
|----------|-------------|
| [API Wiki](https://github.com/tomassirio/wanderer-backend/wiki) | Full API reference with request/response examples |
| [Docker Guide](docs/DOCKER.md) | Building images and running with Docker Compose |
| [CI/CD Workflows](docs/CI-CD.md) | GitHub Actions pipelines for builds, releases, and publishing |
| [Admin Roles](docs/ADMIN_ROLES.md) | Promoting users to admin, bootstrap configuration |
| [Email Configuration](docs/EMAIL_CONFIGURATION.md) | SMTP setup for verification and password reset emails |
| [Weather Integration](docs/WEATHER_INTEGRATION.md) | Google Weather API setup |
| [Comment Reactions](docs/FRONTEND_COMMENT_REACTIONS.md) | Frontend integration guide for comment reactions |
| [Release Notes](https://github.com/tomassirio/wanderer-backend/releases) | Version history and changelog |

Swagger UI is available on each running service at `/swagger-ui.html`.

## 🤝 Contributing

This is a personal project for my pilgrimage, but suggestions and improvements are welcome. Open an issue or submit a pull request.

Before submitting code, please run:

```bash
mvn spotless:apply   # Format code
mvn clean verify     # Run tests and check coverage (target: 80%+)
```

## 📝 License

This project is licensed under the [MIT License](LICENSE). You are free to use, modify, and distribute this software.

---

**¡Buen Camino!** 🥾⛪

# AGENTS.md — AI Coding Agent Guide

## Architecture

CQRS multi-module Maven project (Java 21, Spring Boot 3.5.6). Three independently deployable services share a `commons` module:

- **`commons/`** — JPA entities (`domain/`), DTOs (`dto/`, Java records), MapStruct mappers (`mapper/`), `ApiConstants`, `GlobalExceptionHandler`, `@CurrentUserId` annotation, security configs. All modules depend on this.
- **`wanderer-command/`** (port 8081) — Write operations. Uses an **event-driven pattern**: services publish `DomainEvent` objects via Spring `ApplicationEventPublisher` → `@EventListener` handlers persist data within the transaction → `@TransactionalEventListener(AFTER_COMMIT)` broadcasts via WebSocket. See `command/event/`, `command/handler/`, `command/websocket/`.
- **`wanderer-query/`** (port 8082) — Read-only operations. Simple service → repository → DTO flow, no events.
- **`wanderer-auth/`** (port 8083) — JWT authentication (access + refresh tokens), registration, password reset, email verification.

**Two PostgreSQL databases**: `wanderer_db` (shared by command + query) and `wanderer_auth_db` (auth only). See `docker-compose.yml`.

## Essential Commands

```bash
mvn clean install                    # Full build, all modules
mvn spotless:apply                   # REQUIRED before every commit (Google Java Format AOSP)
mvn clean verify                     # Build + tests + JaCoCo coverage check (80% min)
mvn spring-boot:run -pl wanderer-command  # Run single service locally
```

## Key Patterns

### Command-Side Event Flow
Services **never persist directly** — they publish events, handlers do the writing:
```
Controller → Service.createTrip() → eventPublisher.publishEvent(TripCreatedEvent)
  → TripCreatedEventHandler.handle() [@EventListener, @Transactional(MANDATORY)] persists entity
  → BroadcastableEventListener [@TransactionalEventListener(AFTER_COMMIT)] broadcasts via WebSocket
```
Events implement `DomainEvent`; those needing WebSocket broadcast also implement `Broadcastable`.

### API Constants
All endpoint paths are defined in `commons/.../constants/ApiConstants.java` — never hardcode URL strings in controllers. Use `ApiConstants.TRIPS_PATH`, `ApiConstants.TRIP_BY_ID_ENDPOINT`, etc.

### DTOs & Mappers
DTOs are **Java records** in `commons/dto/`. Mappers live in `commons/mapper/` using `Mappers.getMapper()` singleton pattern. UUID fields are stored as `String` in DTOs, with `@Mapping(expression = "java(...)")` for conversion. Never expose JPA entities in API responses.

### Authenticated User Access
Use `@CurrentUserId UUID userId` parameter annotation (resolved from JWT `Authorization` header). Add `@Parameter(hidden = true)` to hide from OpenAPI docs.

### Controller Structure
Every controller uses: `@RequiredArgsConstructor`, `@Slf4j`, `@Tag(name=...)`, `@RequestMapping(value = ApiConstants.XXX_PATH, produces = APPLICATION_JSON_VALUE)`. Endpoints annotated with `@Operation`, `@ApiResponse`, and `@PreAuthorize("hasAnyRole('ADMIN','USER')")`.

### Database Migrations
Liquibase YAML changesets in `src/main/resources/db/changelog/`. Numbered sequentially (`001-`, `002-`, ...). Registered in `db.changelog-master.yaml`. JSONB columns use Hypersistence Utils.

### Testing
- **Unit tests**: `*Test.java` — JUnit 5 + Mockito, standalone MockMvc for controllers
- **BDD tests**: Cucumber `.feature` files in `src/test/resources/features/`, step definitions mock repositories
- **Integration tests**: `*IT.java` — extend `BaseIntegrationTest` (Testcontainers PostgreSQL)
- JaCoCo enforces 80% coverage; DTOs, entities, configs, and generated code are excluded

### Dependency Injection
Always constructor-based via Lombok `@RequiredArgsConstructor`. Service classes implement an interface (`TripService` → `TripServiceImpl`).

## File Locations

| Concept | Path |
|---------|------|
| Domain entities & enums | `commons/src/main/java/.../domain/` |
| Shared DTOs (records) | `commons/src/main/java/.../dto/` |
| MapStruct mappers | `commons/src/main/java/.../mapper/` |
| API path constants | `commons/.../constants/ApiConstants.java` |
| Global exception handler | `commons/.../exception/GlobalExceptionHandler.java` |
| JWT / `@CurrentUserId` | `commons/.../security/` |
| Command events | `wanderer-command/.../event/` |
| Command event handlers | `wanderer-command/.../handler/` |
| WebSocket broadcasting | `wanderer-command/.../websocket/` |
| Liquibase migrations | `{module}/src/main/resources/db/changelog/` |
| Cucumber features | `{module}/src/test/resources/features/` |


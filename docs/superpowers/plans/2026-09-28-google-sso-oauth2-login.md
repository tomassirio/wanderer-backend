# Google SSO (oauth2Login + Spring Session Redis) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can sign in to Wanderer with Google (web and mobile) and receive the same Wanderer access and refresh tokens that password login returns. Adding a second provider later takes one class plus configuration.

**Architecture:** `wanderer-auth` runs Spring Security `oauth2Login()` on its own filter chain, mounted under `/api/1/auth/oauth2/**`. The OAuth handshake state (`state`, `nonce`, PKCE verifier, `return_to`) lives in a short-lived HTTP session stored in Redis through Spring Session. Every other endpoint stays stateless and keeps using JWTs. When Google redirects back, a per-provider `SsoIdentityMapper` (the Strategy) converts the provider's user into an `ExternalIdentity`. `SsoService.signIn` then finds, links or creates the user, and issues Wanderer tokens. The browser (or the mobile app, via a deep link) receives a one-time code, which the client exchanges at `POST /api/1/auth/sso/exchange` for the `LoginResponse`.

**Tech Stack:** Java 21, Spring Boot 3.5.6, Spring Security 6.5 (`spring-boot-starter-oauth2-client`), Spring Session Data Redis, Liquibase, JUnit 5 + Mockito, Testcontainers (PostgreSQL + Redis).

## Global Constraints

- **Password registration must still require a password.** The `NOT NULL` constraint is dropped only from `user_credentials.password_hash`, because accounts created through SSO have no password. The password path keeps these guarantees:
  - `RegisterRequest.password` stays `@NotBlank @ValidPassword` (unchanged).
  - `email_verification_tokens.password_hash` stays `NOT NULL` (untouched). Task 1 adds an integration test that proves it.
  - `Credential.withPassword(...)` and `UserProvisioningService.provisionWithPassword(...)` reject a null or blank hash before any remote call.
  - Only the SSO path may call `Credential.ssoOnly(...)` or `provisionWithoutPassword(...)`.
  - *Known limit:* the database cannot enforce "password required unless SSO" without a discriminator column, because a CHECK constraint cannot look at another table. The application layer and the tests above enforce it instead.
- Run `mvn spotless:apply` before every commit (Google Java Format, AOSP style).
- JaCoCo requires 80% coverage (`mvn clean verify`).
- Endpoint paths go in `ApiConstants`. Never hardcode URL strings in controllers.
- Services come as an interface plus an `Impl`, using constructor injection with `@RequiredArgsConstructor`.
- Liquibase changesets use YAML, are numbered sequentially and are registered in `db.changelog-master.yaml`.
- Never auto-link an SSO identity to an existing account unless the provider reports `email_verified = true`.
- `return_to` must be an exact match against the `app.sso.allowed-return-uris` allowlist. Any other value falls back to the first allowed URI, so the service is never an open redirect.
- The auth test `src/test/resources/application.properties` **replaces** the main one on the test classpath. Every new required property must be added to both files.
- The Flutter frontend changes (the sign-in button, opening the authorization URL, handling `/auth/sso-callback` and `wanderer://auth/sso-callback`, then calling `/sso/exchange`) are a **separate plan** in `wanderer-frontend`. Only the nginx route from that repo is in scope here (Task 9).

## Target Flow

```
Client ──GET /api/auth/oauth2/authorization/google?return_to=wanderer://auth/sso-callback──▶ auth
auth: validates return_to, stores it + OAuth state/nonce/PKCE in Redis session, 302 → accounts.google.com
Google ──302 /api/1/auth/oauth2/callback/google?code&state──▶ auth
auth: Spring swaps the code for tokens (client secret) and checks the ID token (sig/iss/aud/nonce) → OidcUser
      SsoAuthenticationSuccessHandler → GoogleSsoIdentityMapper → ExternalIdentity
      SsoService.signIn → find/link/create → LoginResponse → SsoLoginCodeStore (Redis, 60s, single use)
      session invalidated, 302 → return_to?code=<one-time code>   (on failure: return_to?error=sso_failed)
Client ──POST /api/auth/sso/exchange {code}──▶ auth → LoginResponse (same shape as /login)
```

## File Map

| File | Responsibility |
|---|---|
| `wanderer-auth/src/main/resources/db/changelog/changesets/009-create-user-identities.yaml` | New `user_identities` table; make `user_credentials.password_hash` nullable |
| `wanderer-auth/.../auth/domain/UserIdentity.java` | JPA entity linking (provider, subject) to a user |
| `wanderer-auth/.../auth/repository/UserIdentityRepository.java` | Look up an identity by provider and subject |
| `wanderer-auth/.../auth/domain/Credential.java` (modify) | Nullable hash; `withPassword`, `ssoOnly`, `hasPassword` |
| `wanderer-auth/.../auth/service/UserProvisioningService.java` + `impl/UserProvisioningServiceImpl.java` | Create the user in command, fetch it from query, save the credential, roll back on failure (moved out of `verifyEmail`) |
| `wanderer-auth/.../auth/service/TokenService.java` + `impl/TokenServiceImpl.java` (modify) | `issueLoginTokens(User, Set<Role>)` |
| `wanderer-auth/.../auth/sso/ExternalIdentity.java` | Provider-neutral identity record |
| `wanderer-auth/.../auth/sso/SsoIdentityMapper.java` | **Strategy interface** |
| `wanderer-auth/.../auth/sso/GoogleSsoIdentityMapper.java` | Google strategy |
| `wanderer-auth/.../auth/sso/UsernameGenerator.java` | Unique username from an email address |
| `wanderer-auth/.../auth/service/SsoService.java` + `impl/SsoServiceImpl.java` | Find, link or create the user, then issue tokens |
| `wanderer-auth/.../auth/config/SsoProperties.java` + `SsoConfig.java` | `app.sso.*` properties |
| `wanderer-auth/.../auth/sso/SsoLoginCodeStore.java` | One-time code ↔ `LoginResponse` in Redis |
| `wanderer-auth/.../auth/dto/SsoExchangeRequest.java` + `controller/SsoController.java` | `POST /sso/exchange` |
| `wanderer-auth/.../auth/sso/SsoReturnUris.java` | Allowlist check and `return_to` stored in the session |
| `wanderer-auth/.../auth/sso/ReturnToAuthorizationRequestResolver.java` | Captures `return_to` and adds PKCE |
| `wanderer-auth/.../auth/sso/SsoAuthenticationSuccessHandler.java` / `SsoAuthenticationFailureHandler.java` | Redirect back with a code or an error |
| `wanderer-auth/.../auth/config/SecurityConfig.java` (modify) | New `@Order(1)` SSO chain; existing chain becomes `@Order(2)` |
| `commons/.../constants/ApiConstants.java` (modify) | SSO path constants |
| `wanderer-auth/pom.xml` (modify) | `spring-boot-starter-oauth2-client`, `spring-session-data-redis` |
| properties, chart, compose, workflow, `docs/SSO.md` | Configuration and operations |

`...` = `src/main/java/com/tomassirio/wanderer` (tests: `src/test/java/com/tomassirio/wanderer`).

---

### Task 1: Schema — `user_identities` table and nullable password hash

**Files:**
- Create: `wanderer-auth/src/main/resources/db/changelog/changesets/009-create-user-identities.yaml`
- Modify: `wanderer-auth/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/domain/UserIdentity.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/repository/UserIdentityRepository.java`
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/domain/Credential.java:664-665`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/repository/SsoSchemaIT.java`

**Interfaces:**
- Produces: `UserIdentity` (fields `id: UUID`, `userId: UUID`, `provider: String`, `subject: String`, `email: String`, `createdAt: Instant`, with a Lombok builder); `UserIdentityRepository.findByProviderAndSubject(String provider, String subject): Optional<UserIdentity>`.

- [ ] **Step 1: Write the failing integration test**

```java
package com.tomassirio.wanderer.auth.repository;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.auth.AuthApplication;
import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.EmailVerificationToken;
import com.tomassirio.wanderer.auth.domain.UserIdentity;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.security.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = AuthApplication.class)
@Import(TestConfig.class)
@TestPropertySource(
        properties =
                "jwt.secret=test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum")
class SsoSchemaIT extends BaseIntegrationTest {

    @MockitoBean private WandererCommandClient wandererCommandClient;
    @MockitoBean private WandererQueryClient wandererQueryClient;

    @Autowired private CredentialRepository credentialRepository;
    @Autowired private UserIdentityRepository userIdentityRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    void credentialWithoutPasswordHash_canBeSaved() {
        UUID userId = UUID.randomUUID();
        credentialRepository.saveAndFlush(
                Credential.builder()
                        .userId(userId)
                        .email(userId + "@sso.test")
                        .enabled(true)
                        .roles(Set.of(Role.USER))
                        .build());

        assertNull(credentialRepository.findById(userId).orElseThrow().getPasswordHash());
    }

    @Test
    void userIdentity_isUniquePerProviderAndSubject() {
        UUID userId = UUID.randomUUID();
        credentialRepository.saveAndFlush(
                Credential.builder()
                        .userId(userId)
                        .email(userId + "@sso.test")
                        .enabled(true)
                        .roles(Set.of(Role.USER))
                        .build());
        userIdentityRepository.saveAndFlush(identity(userId, "google", "sub-" + userId));

        assertTrue(
                userIdentityRepository
                        .findByProviderAndSubject("google", "sub-" + userId)
                        .isPresent());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        userIdentityRepository.saveAndFlush(
                                identity(userId, "google", "sub-" + userId)));
    }

    @Test
    void emailVerificationToken_stillRequiresPasswordHash() {
        EmailVerificationToken token =
                EmailVerificationToken.builder()
                        .tokenId(UUID.randomUUID())
                        .email("pending@test.com")
                        .username("pending")
                        .passwordHash(null)
                        .tokenHash("hash-" + UUID.randomUUID())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .verified(false)
                        .build();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> emailVerificationTokenRepository.saveAndFlush(token));
    }

    private UserIdentity identity(UUID userId, String provider, String subject) {
        return UserIdentity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider(provider)
                .subject(subject)
                .email(userId + "@sso.test")
                .build();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -pl wanderer-auth -am verify -Dit.test=SsoSchemaIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class UserIdentity`.

- [ ] **Step 3: Write the migration**

`009-create-user-identities.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 009-create-user-identities
      author: tomassirio
      comment: SSO support. Provider identities linked to credentials; password becomes optional for SSO-only accounts
      changes:
        - dropNotNullConstraint:
            tableName: user_credentials
            columnName: password_hash
            columnDataType: varchar(255)
        - createTable:
            tableName: user_identities
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: uuid
                  constraints:
                    nullable: false
                    foreignKeyName: fk_user_identities_user_credentials
                    references: user_credentials(user_id)
                    deleteCascade: true
              - column:
                  name: provider
                  type: varchar(50)
                  constraints:
                    nullable: false
              - column:
                  name: subject
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: email
                  type: varchar(255)
              - column:
                  name: created_at
                  type: timestamp
                  constraints:
                    nullable: false
        - addUniqueConstraint:
            tableName: user_identities
            columnNames: provider, subject
            constraintName: uc_user_identities_provider_subject
        - createIndex:
            indexName: idx_user_identities_user_id
            tableName: user_identities
            columns:
              - column:
                  name: user_id
      rollback:
        - dropTable:
            tableName: user_identities
        # Fails if SSO-only credentials exist. Intended: delete or give them passwords first.
        - addNotNullConstraint:
            tableName: user_credentials
            columnName: password_hash
            columnDataType: varchar(255)
```

Append to `db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/changesets/009-create-user-identities.yaml
```

- [ ] **Step 4: Add the entity and repository, and make the hash nullable**

`UserIdentity.java`:

```java
package com.tomassirio.wanderer.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Links an external SSO identity (provider + provider subject) to a Wanderer user. */
@Entity
@Table(name = "user_identities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "email")
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

`UserIdentityRepository.java`:

```java
package com.tomassirio.wanderer.auth.repository;

import com.tomassirio.wanderer.auth.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);
}
```

In `Credential.java`, replace:

```java
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
```

with:

```java
    /** Null for SSO-only accounts. Password registration always sets it (see withPassword). */
    @Column(name = "password_hash")
    private String passwordHash;
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -pl wanderer-auth -am verify -Dit.test=SsoSchemaIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/src/main/resources/db/changelog wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/domain wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/repository/UserIdentityRepository.java wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/repository/SsoSchemaIT.java
git commit -m "feat(auth): add user_identities table and allow SSO-only credentials"
```

---

### Task 2: Password guards — explicit factories and null-hash handling

**Files:**
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/domain/Credential.java`
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/AuthServiceImpl.java` (`login` lines 97-100, `changePassword` lines 337-340)
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/domain/CredentialTest.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/AuthServiceImplTest.java`

**Interfaces:**
- Produces: `static Credential Credential.withPassword(UUID userId, String email, String passwordHash)` (throws `IllegalArgumentException` on a null or blank hash); `static Credential Credential.ssoOnly(UUID userId, String email)`; `boolean Credential.hasPassword()`. Both factories create the credential enabled, with roles `Set.of(Role.USER)`.

- [ ] **Step 1: Write the failing tests**

`CredentialTest.java`:

```java
package com.tomassirio.wanderer.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.commons.security.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void withPassword_setsHashRoleAndEnabled() {
        Credential credential = Credential.withPassword(userId, "a@b.com", "$2a$hash");

        assertEquals("$2a$hash", credential.getPasswordHash());
        assertTrue(credential.hasPassword());
        assertTrue(credential.isEnabled());
        assertEquals(Set.of(Role.USER), credential.getRoles());
    }

    @Test
    void withPassword_rejectsNullHash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Credential.withPassword(userId, "a@b.com", null));
    }

    @Test
    void withPassword_rejectsBlankHash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Credential.withPassword(userId, "a@b.com", "  "));
    }

    @Test
    void ssoOnly_hasNoPassword() {
        Credential credential = Credential.ssoOnly(userId, "a@b.com");

        assertNull(credential.getPasswordHash());
        assertFalse(credential.hasPassword());
        assertTrue(credential.isEnabled());
        assertEquals(Set.of(Role.USER), credential.getRoles());
    }
}
```

Add to `AuthServiceImplTest.java`, after `login_whenPasswordIncorrect_shouldThrowIllegalArgumentException`:

```java
    @Test
    void login_whenCredentialHasNoPassword_shouldRejectWithoutCallingEncoder() {
        Credential ssoOnly = Credential.ssoOnly(testUserInfo.id(), "user@email.com");
        when(wandererQueryClient.getUserByUsername(testUserInfo.username(), "basic"))
                .thenReturn(testUserInfo);
        when(credentialRepository.findById(testUserInfo.id())).thenReturn(Optional.of(ssoOnly));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> authService.login(testUserInfo.username(), "anything", "127.0.0.1"));

        assertEquals("Invalid credentials", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginAttemptService).recordFailedLogin(testUserInfo.username(), "127.0.0.1");
    }

    @Test
    void changePassword_whenCredentialHasNoPassword_shouldThrowWithHint() {
        Credential ssoOnly = Credential.ssoOnly(testUserInfo.id(), "user@email.com");
        when(credentialRepository.findById(testUserInfo.id())).thenReturn(Optional.of(ssoOnly));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                authService.changePassword(
                                        testUserInfo.id(), "whatever", "NewPass123!"));

        assertEquals(
                "No password set for this account. Use password reset to set one.",
                ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(credentialRepository, never()).save(any());
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -pl wanderer-auth -am test -Dtest='CredentialTest,AuthServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: method ssoOnly`.

- [ ] **Step 3: Implement**

Add to `Credential.java` (inside the class, after the fields):

```java
    /** Credential for password registration. A password hash is mandatory on this path. */
    public static Credential withPassword(UUID userId, String email, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        return Credential.builder()
                .userId(userId)
                .email(email)
                .passwordHash(passwordHash)
                .enabled(true)
                .roles(Set.of(Role.USER))
                .build();
    }

    /** Credential for an account created via SSO. It has no password until the user sets one. */
    public static Credential ssoOnly(UUID userId, String email) {
        return Credential.builder()
                .userId(userId)
                .email(email)
                .enabled(true)
                .roles(Set.of(Role.USER))
                .build();
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
```

In `AuthServiceImpl.login`, replace:

```java
        if (!passwordEncoder.matches(password, cred.getPasswordHash())) {
```

with:

```java
        if (!cred.hasPassword() || !passwordEncoder.matches(password, cred.getPasswordHash())) {
```

In `AuthServiceImpl.changePassword`, directly after `Credential cred = maybeCred.get();`, insert:

```java
        if (!cred.hasPassword()) {
            throw new IllegalArgumentException(
                    "No password set for this account. Use password reset to set one.");
        }
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `mvn -pl wanderer-auth -am test -Dtest='CredentialTest,AuthServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/domain/Credential.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/AuthServiceImpl.java wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/domain/CredentialTest.java wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/AuthServiceImplTest.java
git commit -m "feat(auth): guard password paths against SSO-only credentials"
```

---

### Task 3: Extract `UserProvisioningService` and `TokenService.issueLoginTokens`

The code in `verifyEmail` that creates the user and rolls back on failure moves into one service, so SSO sign-up reuses it without a second copy. The password path gets its own method, which refuses a missing hash.

**Files:**
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/UserProvisioningService.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/UserProvisioningServiceImpl.java`
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/TokenService.java`
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/TokenServiceImpl.java`
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/AuthServiceImpl.java` (field list; `verifyEmail` lines 165-251)
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/UserProvisioningServiceImplTest.java`
- Modify test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/AuthServiceImplTest.java`
- Modify test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/TokenServiceImplTest.java`

**Interfaces:**
- Consumes: `Credential.withPassword`, `Credential.ssoOnly` (Task 2).
- Produces:
  - `User UserProvisioningService.provisionWithPassword(String username, String email, String displayName, String passwordHash)` — throws `IllegalArgumentException` on a null or blank hash, and `IllegalStateException` on remote or DB failure (after rolling back).
  - `User UserProvisioningService.provisionWithoutPassword(String username, String email, String displayName)`.
  - `LoginResponse TokenService.issueLoginTokens(User user, Set<Role> roles)` — token type `"Bearer"`, `expiresIn = jwtService.getExpirationMs()`, `username = user.getUsername()`.

- [ ] **Step 1: Write the failing tests**

`UserProvisioningServiceImplTest.java`:

```java
package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.impl.UserProvisioningServiceImpl;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import feign.FeignException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceImplTest {

    @Mock private CredentialRepository credentialRepository;
    @Mock private WandererCommandClient wandererCommandClient;
    @Mock private WandererQueryClient wandererQueryClient;
    @InjectMocks private UserProvisioningServiceImpl provisioningService;

    private final UUID userId = UUID.randomUUID();
    private final UserBasicInfo info = new UserBasicInfo(userId, "testuser");

    @Test
    void provisionWithPassword_createsUserAndSavesCredentialWithHash() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        when(wandererCommandClient.createUser(payload.capture())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(info);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        User user =
                provisioningService.provisionWithPassword(
                        "testuser", "t@e.com", "TestUser", "$2a$hash");

        assertEquals(userId, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("testuser", payload.getValue().get("username"));
        assertEquals("TestUser", payload.getValue().get("displayName"));
        assertEquals("t@e.com", payload.getValue().get("email"));
        ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(saved.capture());
        assertEquals("$2a$hash", saved.getValue().getPasswordHash());
        assertEquals(Set.of(Role.USER), saved.getValue().getRoles());
    }

    @Test
    void provisionWithPassword_rejectsNullHashBeforeAnyRemoteCall() {
        assertThrows(
                IllegalArgumentException.class,
                () -> provisioningService.provisionWithPassword("u", "e@x.com", "u", null));
        verifyNoInteractions(wandererCommandClient, wandererQueryClient, credentialRepository);
    }

    @Test
    void provisionWithPassword_rejectsBlankHashBeforeAnyRemoteCall() {
        assertThrows(
                IllegalArgumentException.class,
                () -> provisioningService.provisionWithPassword("u", "e@x.com", "u", " "));
        verifyNoInteractions(wandererCommandClient, wandererQueryClient, credentialRepository);
    }

    @Test
    void provisionWithoutPassword_savesCredentialWithoutHash() {
        when(wandererCommandClient.createUser(any())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(info);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        provisioningService.provisionWithoutPassword("testuser", "t@e.com", "Test User");

        ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(saved.capture());
        assertNull(saved.getValue().getPasswordHash());
    }

    @Test
    void whenCreateUserFails_throwsAndSavesNothing() {
        when(wandererCommandClient.createUser(any())).thenThrow(FeignException.class);

        assertThrows(
                IllegalStateException.class,
                () -> provisioningService.provisionWithoutPassword("u", "e@x.com", "u"));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void whenFetchUserFails_deletesCreatedUserAndThrows() {
        when(wandererCommandClient.createUser(any())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenThrow(FeignException.class);

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> provisioningService.provisionWithoutPassword("u", "e@x.com", "u"));

        assertEquals("Failed to fetch created user from query service", ex.getMessage());
        verify(wandererCommandClient).deleteUser(userId);
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void whenCredentialSaveFails_deletesCreatedUserAndThrows() {
        when(wandererCommandClient.createUser(any())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(info);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());
        when(credentialRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThrows(
                IllegalStateException.class,
                () -> provisioningService.provisionWithoutPassword("u", "e@x.com", "u"));
        verify(wandererCommandClient).deleteUser(userId);
    }
}
```

Changes to `AuthServiceImplTest.java`:
1. Replace `@Mock private WandererCommandClient wandererCommandClient;` with `@Mock private UserProvisioningService userProvisioningService;`, and delete the `WandererCommandClient` import.
2. In `setUp`, replace the constructor argument `wandererCommandClient,` with `userProvisioningService,`.
3. Delete the line `verify(wandererCommandClient, never()).createUser(any());` in the register test (currently line 303). Registration never provisioned a user, so the assertion no longer applies.
4. Delete the five `verifyEmail_*` tests (currently lines 368-477). Their rollback cases now live in `UserProvisioningServiceImplTest`. Add these in their place:

```java
    @Test
    void verifyEmail_whenValidToken_shouldProvisionWithPasswordAndReturnLoginResponse() {
        String verificationToken = "verification.token";
        String[] verificationData = new String[] {"test@example.com", "TestUser", "hashedPassword"};
        User created = toUser(testUserInfo);
        LoginResponse expected =
                new LoginResponse(
                        "jwt.access.token", "refresh.token", "Bearer", 3600000L, "testuser");

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userProvisioningService.provisionWithPassword(
                        "testuser", "test@example.com", "TestUser", "hashedPassword"))
                .thenReturn(created);
        when(tokenService.issueLoginTokens(created, Set.of(Role.USER))).thenReturn(expected);

        LoginResponse result = authService.verifyEmail(verificationToken);

        assertEquals(expected, result);
        verify(tokenService).markEmailVerificationTokenAsVerified(verificationToken);
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void verifyEmail_whenEmailAlreadyInUse_shouldThrowException() {
        String verificationToken = "verification.token";
        String[] verificationData =
                new String[] {"existing@example.com", "testuser", "hashedPassword"};

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(testCredential));

        assertThrows(IllegalStateException.class, () -> authService.verifyEmail(verificationToken));
        verify(userProvisioningService, never()).provisionWithPassword(any(), any(), any(), any());
    }

    @Test
    void verifyEmail_whenProvisioningFails_shouldNotMarkTokenVerified() {
        String verificationToken = "verification.token";
        String[] verificationData = new String[] {"test@example.com", "testuser", "hashedPassword"};

        when(tokenService.validateEmailVerificationToken(verificationToken))
                .thenReturn(verificationData);
        when(credentialRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userProvisioningService.provisionWithPassword(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> authService.verifyEmail(verificationToken));
        verify(tokenService, never()).markEmailVerificationTokenAsVerified(any());
    }
```

Add to `TokenServiceImplTest.java`:

```java
    @Test
    void issueLoginTokens_shouldReturnBearerResponseWithRolesAndRefreshToken() {
        User user = new User();
        user.setId(testUserId);
        user.setUsername("testuser");
        when(jwtService.generateTokenWithJti(eq(user), any(), eq(Set.of(Role.ADMIN, Role.USER))))
                .thenReturn("access.jwt");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtService.getExpirationMs()).thenReturn(900000L);

        LoginResponse response = tokenService.issueLoginTokens(user, Set.of(Role.ADMIN, Role.USER));

        assertEquals("access.jwt", response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900000L, response.expiresIn());
        assertEquals("testuser", response.username());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
```

(Add any missing imports to `TokenServiceImplTest`: `LoginResponse`, `User`, `Role`, `Set`, `eq`, `assertNotNull`.)

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -pl wanderer-auth -am test -Dtest='UserProvisioningServiceImplTest,AuthServiceImplTest,TokenServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class UserProvisioningService`.

- [ ] **Step 3: Implement**

`UserProvisioningService.java`:

```java
package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.commons.domain.User;

/**
 * Creates a Wanderer user across services: domain user in command, read back from query, and a
 * credential in the auth DB. Compensates by deleting the domain user if a later step fails.
 */
public interface UserProvisioningService {

    /**
     * Password registration path.
     *
     * @throws IllegalArgumentException if passwordHash is null or blank
     * @throws IllegalStateException if any step fails (after rollback)
     */
    User provisionWithPassword(
            String username, String email, String displayName, String passwordHash);

    /**
     * SSO path. The credential has no password.
     *
     * @throws IllegalStateException if any step fails (after rollback)
     */
    User provisionWithoutPassword(String username, String email, String displayName);
}
```

`UserProvisioningServiceImpl.java`. The body is moved from `AuthServiceImpl.verifyEmail` steps 1–3 with the error messages unchanged:

```java
package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.UserProvisioningService;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private final CredentialRepository credentialRepository;
    private final WandererCommandClient wandererCommandClient;
    private final WandererQueryClient wandererQueryClient;

    @Override
    public User provisionWithPassword(
            String username, String email, String displayName, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password is required for password-based registration");
        }
        return provision(
                username,
                email,
                displayName,
                userId -> Credential.withPassword(userId, email, passwordHash));
    }

    @Override
    public User provisionWithoutPassword(String username, String email, String displayName) {
        return provision(username, email, displayName, userId -> Credential.ssoOnly(userId, email));
    }

    private User provision(
            String username,
            String email,
            String displayName,
            Function<UUID, Credential> credentialFactory) {
        // 1) Create the domain user via the command service (returns UUID)
        var payload = Map.of("username", username, "email", email, "displayName", displayName);
        UUID createdUserId;
        try {
            createdUserId = wandererCommandClient.createUser(payload);
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to create user in command service", e);
        }

        // 2) Fetch the created user from query service to get full User object
        User createdUser;
        try {
            UserBasicInfo userInfo = wandererQueryClient.getUserById(createdUserId, "basic");
            createdUser = new User();
            createdUser.setId(userInfo.id());
            createdUser.setUsername(userInfo.username());
        } catch (FeignException e) {
            try {
                wandererCommandClient.deleteUser(createdUserId);
            } catch (FeignException ex) {
                throw new IllegalStateException(
                        "Failed to fetch created user and failed to rollback: " + ex.getMessage(),
                        e);
            }
            throw new IllegalStateException("Failed to fetch created user from query service", e);
        }

        // 3) Create credential in auth DB — compensate on failure
        try {
            if (credentialRepository.findById(createdUser.getId()).isPresent()) {
                throw new IllegalArgumentException(
                        "Credentials already exist for user: " + createdUser.getId());
            }
            credentialRepository.save(credentialFactory.apply(createdUser.getId()));
        } catch (Exception e) {
            try {
                wandererCommandClient.deleteUser(createdUserId);
            } catch (FeignException ex) {
                throw new IllegalStateException(
                        "Failed to create credentials and failed to rollback user creation: "
                                + ex.getMessage(),
                        e);
            }
            throw new IllegalStateException(
                    "Failed to create credentials, rolled back user creation", e);
        }
        return createdUser;
    }
}
```

Add to `TokenService.java`:

```java
    /**
     * Issues a fresh access token (with JTI) and refresh token for a user.
     *
     * @param user the authenticated user (id and username required)
     * @param roles roles to embed in the access token
     * @return a Bearer LoginResponse
     */
    LoginResponse issueLoginTokens(User user, Set<Role> roles);
```

(Imports: `com.tomassirio.wanderer.auth.dto.LoginResponse`, `com.tomassirio.wanderer.commons.domain.User`, `com.tomassirio.wanderer.commons.security.Role`, `java.util.Set`.)

Add to `TokenServiceImpl.java` (and import `LoginResponse`):

```java
    @Override
    @Transactional
    public LoginResponse issueLoginTokens(User user, Set<Role> roles) {
        String accessToken =
                jwtService.generateTokenWithJti(user, UUID.randomUUID().toString(), roles);
        String refreshToken = createRefreshToken(user.getId());
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getExpirationMs(),
                user.getUsername());
    }
```

In `AuthServiceImpl.java`, replace the field `private final WandererCommandClient wandererCommandClient;` with `private final UserProvisioningService userProvisioningService;` in the same position, and remove the unused `WandererCommandClient` import. Replace the whole `verifyEmail` method with:

```java
    /**
     * Verify email and complete user registration. Validates the verification token, provisions
     * the user with the password chosen at registration, and returns login tokens.
     */
    public LoginResponse verifyEmail(String token) {
        String[] verificationData = tokenService.validateEmailVerificationToken(token);
        String email = verificationData[0];
        String originalUsername = verificationData[1];
        String passwordHash = verificationData[2];

        // Normalize username to lowercase; keep original casing as displayName
        String username = originalUsername.toLowerCase(Locale.ROOT);

        if (credentialRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email already in use: " + email);
        }

        User createdUser =
                userProvisioningService.provisionWithPassword(
                        username, email, originalUsername, passwordHash);

        tokenService.markEmailVerificationTokenAsVerified(token);
        return tokenService.issueLoginTokens(createdUser, Set.of(Role.USER));
    }
```

(Remove any imports this leaves unused, such as `Map`. `mvn spotless:apply` does not remove unused imports.)

- [ ] **Step 4: Run all auth unit tests and confirm they pass**

Run: `mvn -pl wanderer-auth -am test -Djacoco.skip=true`
Expected: all PASS (the Cucumber `verify-email` scenarios run in `verify`. Task 10 runs them).

- [ ] **Step 5: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service
git commit -m "refactor(auth): extract user provisioning and token issuing for reuse by SSO"
```

---

### Task 4: Strategy — `SsoIdentityMapper` and the Google implementation

**Files:**
- Modify: `wanderer-auth/pom.xml` (dependencies)
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/ExternalIdentity.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/SsoIdentityMapper.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/GoogleSsoIdentityMapper.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/GoogleSsoIdentityMapperTest.java`

**Interfaces:**
- Produces:
  - `record ExternalIdentity(String provider, String subject, String email, boolean emailVerified, String name)`.
  - `interface SsoIdentityMapper { String provider(); ExternalIdentity map(OAuth2User user); }`. `provider()` must equal the Spring `registrationId` (`"google"`).
  - `GoogleSsoIdentityMapper.PROVIDER = "google"`.

- [ ] **Step 1: Add the dependencies**

In `wanderer-auth/pom.xml`, after `spring-boot-starter-oauth2-resource-server`, add the following. Spring Boot manages both versions:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-client</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.session</groupId>
            <artifactId>spring-session-data-redis</artifactId>
        </dependency>
```

Until Task 8 adds a `spring.security.oauth2.client.registration.*` property, Boot creates no client registration beans. Spring Session's default `RedisSessionRepository` does not connect to Redis at startup. The existing context therefore still boots.

- [ ] **Step 2: Write the failing test**

```java
package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class GoogleSsoIdentityMapperTest {

    private final GoogleSsoIdentityMapper mapper = new GoogleSsoIdentityMapper();

    @Test
    void provider_isGoogleRegistrationId() {
        assertEquals("google", mapper.provider());
    }

    @Test
    void map_readsOidcClaims() {
        ExternalIdentity identity = mapper.map(oidcUser(true));

        assertEquals("google", identity.provider());
        assertEquals("1234567890", identity.subject());
        assertEquals("ana@gmail.com", identity.email());
        assertTrue(identity.emailVerified());
        assertEquals("Ana Maria", identity.name());
    }

    @Test
    void map_whenEmailNotVerified_reportsFalse() {
        assertFalse(mapper.map(oidcUser(false)).emailVerified());
    }

    @Test
    void map_whenNotOidcUser_rejects() {
        var plainUser =
                new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, Map.of("id", "1"), "id");

        assertThrows(IllegalArgumentException.class, () -> mapper.map(plainUser));
    }

    private DefaultOidcUser oidcUser(boolean emailVerified) {
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("id-token")
                        .subject("1234567890")
                        .claim("email", "ana@gmail.com")
                        .claim("email_verified", emailVerified)
                        .claim("name", "Ana Maria")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
        return new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken);
    }
}
```

- [ ] **Step 3: Run the test and confirm it fails**

Run: `mvn -pl wanderer-auth -am test -Dtest=GoogleSsoIdentityMapperTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class GoogleSsoIdentityMapper`.

- [ ] **Step 4: Implement**

`ExternalIdentity.java`:

```java
package com.tomassirio.wanderer.auth.sso;

/**
 * Provider-neutral identity produced by an {@link SsoIdentityMapper}.
 *
 * @param provider registration id, e.g. "google"
 * @param subject stable, provider-unique user id (never the email)
 * @param email email reported by the provider, may be null
 * @param emailVerified whether the provider vouches for the email
 * @param name display name, may be null
 */
public record ExternalIdentity(
        String provider, String subject, String email, boolean emailVerified, String name) {}
```

`SsoIdentityMapper.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Strategy for turning a provider's authenticated user into an {@link ExternalIdentity}. One
 * implementation per SSO provider. Adding a provider = one implementation + a
 * spring.security.oauth2.client.registration.&lt;id&gt;.* block.
 */
public interface SsoIdentityMapper {

    /** Must match the Spring Security OAuth2 client registration id. */
    String provider();

    /**
     * @throws IllegalArgumentException if the user cannot be mapped
     */
    ExternalIdentity map(OAuth2User user);
}
```

`GoogleSsoIdentityMapper.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/** Google is OpenID Connect: Spring has already validated the ID token before this runs. */
@Component
public class GoogleSsoIdentityMapper implements SsoIdentityMapper {

    public static final String PROVIDER = "google";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ExternalIdentity map(OAuth2User user) {
        if (!(user instanceof OidcUser oidcUser)) {
            throw new IllegalArgumentException("Google login must use OpenID Connect");
        }
        return new ExternalIdentity(
                PROVIDER,
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                oidcUser.getFullName());
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -pl wanderer-auth -am test -Dtest=GoogleSsoIdentityMapperTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 6: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/pom.xml wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso
git commit -m "feat(auth): add SSO identity mapper strategy with Google implementation"
```

---

### Task 5: `UsernameGenerator`

**Files:**
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/UsernameGenerator.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/UsernameGeneratorTest.java`

**Interfaces:**
- Produces: `String UsernameGenerator.generate(String email)` returns a free lowercase `[a-z0-9_]` username of 3–50 characters. It throws `IllegalStateException` when it runs out of attempts or the query service fails. `static String baseFrom(String email)` is package-private.

- [ ] **Step 1: Write the failing test**

```java
package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsernameGeneratorTest {

    @Mock private WandererQueryClient wandererQueryClient;
    @InjectMocks private UsernameGenerator generator;

    private final UserBasicInfo taken = new UserBasicInfo(UUID.randomUUID(), "taken");

    @Test
    void baseFrom_lowercasesAndStripsInvalidCharacters() {
        assertEquals("anamariatrips", UsernameGenerator.baseFrom("Ana.Maria+trips@gmail.com"));
    }

    @Test
    void baseFrom_padsShortLocalPart() {
        assertEquals("wandererab", UsernameGenerator.baseFrom("ab@x.com"));
    }

    @Test
    void baseFrom_truncatesLongLocalPart() {
        assertEquals(40, UsernameGenerator.baseFrom("a".repeat(80) + "@x.com").length());
    }

    @Test
    void generate_returnsBaseWhenAvailable() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenReturn(null);

        assertEquals("ana", generator.generate("ana@gmail.com"));
    }

    @Test
    void generate_treats404AsAvailable() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenThrow(notFound());

        assertEquals("ana", generator.generate("ana@gmail.com"));
    }

    @Test
    void generate_appendsSuffixWhenBaseTaken() {
        when(wandererQueryClient.getUserByUsername("ana", "basic")).thenReturn(taken);
        when(wandererQueryClient.getUserByUsername(argThat(u -> u.startsWith("ana_")), eq("basic")))
                .thenReturn(null);

        String username = generator.generate("ana@gmail.com");

        assertTrue(username.matches("ana_\\d{4}"), username);
    }

    @Test
    void generate_whenEverythingTaken_throws() {
        when(wandererQueryClient.getUserByUsername(anyString(), eq("basic"))).thenReturn(taken);

        assertThrows(IllegalStateException.class, () -> generator.generate("ana@gmail.com"));
    }

    @Test
    void generate_whenQueryServiceFails_throws() {
        when(wandererQueryClient.getUserByUsername("ana", "basic"))
                .thenThrow(FeignException.class);

        assertThrows(IllegalStateException.class, () -> generator.generate("ana@gmail.com"));
    }

    private FeignException notFound() {
        Request request =
                Request.create(
                        Request.HttpMethod.GET,
                        "/users/username/ana",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
        return new FeignException.NotFound("not found", request, null, null);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -pl wanderer-auth -am test -Dtest=UsernameGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class UsernameGenerator`.

- [ ] **Step 3: Implement**

```java
package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import feign.FeignException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Derives a free username from an SSO email: local part, cleaned, suffixed on collision. */
@Component
@RequiredArgsConstructor
public class UsernameGenerator {

    static final int MIN_LENGTH = 3;
    static final int MAX_BASE_LENGTH = 40; // + "_1234" stays under the 50-char limit
    static final int MAX_ATTEMPTS = 5;

    private final WandererQueryClient wandererQueryClient;

    public String generate(String email) {
        String base = baseFrom(email);
        if (isAvailable(base)) {
            return base;
        }
        // ponytail: random suffix + availability check can race with a concurrent signup; the
        // command service's unique username constraint rejects the loser, which surfaces as a
        // failed SSO login that succeeds on retry.
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = base + "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique username");
    }

    static String baseFrom(String email) {
        String local = email == null ? "" : email.split("@", 2)[0];
        String cleaned = local.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (cleaned.length() > MAX_BASE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_BASE_LENGTH);
        }
        if (cleaned.length() < MIN_LENGTH) {
            cleaned = "wanderer" + cleaned;
        }
        return cleaned;
    }

    private boolean isAvailable(String username) {
        try {
            return wandererQueryClient.getUserByUsername(username, "basic") == null;
        } catch (FeignException e) {
            if (e.status() == 404) {
                return true;
            }
            throw new IllegalStateException("Failed to check username availability", e);
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -pl wanderer-auth -am test -Dtest=UsernameGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: `Tests run: 8, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/UsernameGenerator.java wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/UsernameGeneratorTest.java
git commit -m "feat(auth): generate unique usernames for SSO sign-ups"
```

---

### Task 6: `SsoService.signIn` — find, link or create

**Files:**
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/SsoService.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/SsoServiceImpl.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/SsoServiceImplTest.java`

**Interfaces:**
- Consumes: `UserIdentityRepository.findByProviderAndSubject` (Task 1), `Credential.getRoles/isEnabled` (Task 2), `UserProvisioningService.provisionWithoutPassword` and `TokenService.issueLoginTokens` (Task 3), `ExternalIdentity` (Task 4), `UsernameGenerator.generate` (Task 5).
- Produces: `LoginResponse SsoService.signIn(ExternalIdentity identity)`. It throws `IllegalArgumentException` for an unverified email or a disabled account, and `IllegalStateException` for provisioning or query failures.

Sign-in rules:
1. The identity (provider, subject) is already linked: use that account.
2. Otherwise the email must be present and `emailVerified` must be true. If not, reject.
3. A credential with the same email exists: link the identity to it. Credentials exist only after email verification or SSO, so the email is proven on both sides.
4. Otherwise, provision a new SSO-only user and link the identity.
5. Reject disabled accounts. Tokens carry the credential's stored roles.

If saving the identity fails after step 4 has created the user, the next attempt takes the step-3 path (the email matches) and links the account then. No manual cleanup is needed.

- [ ] **Step 1: Write the failing test**

```java
package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.UserIdentity;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.repository.UserIdentityRepository;
import com.tomassirio.wanderer.auth.service.impl.SsoServiceImpl;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;
import com.tomassirio.wanderer.auth.sso.UsernameGenerator;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsoServiceImplTest {

    @Mock private UserIdentityRepository userIdentityRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private UserProvisioningService userProvisioningService;
    @Mock private UsernameGenerator usernameGenerator;
    @Mock private WandererQueryClient wandererQueryClient;
    @Mock private TokenService tokenService;
    @InjectMocks private SsoServiceImpl ssoService;

    private final UUID userId = UUID.randomUUID();
    private final ExternalIdentity verified =
            new ExternalIdentity("google", "sub-1", "ana@gmail.com", true, "Ana Maria");
    private final LoginResponse tokens =
            new LoginResponse("access", "refresh", "Bearer", 900000L, "ana");

    @Test
    void signIn_whenIdentityLinked_issuesTokensWithStoredRoles() {
        Credential credential = Credential.ssoOnly(userId, "ana@gmail.com");
        credential.setRoles(Set.of(Role.ADMIN, Role.USER));
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(link()));
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(credential));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.ADMIN, Role.USER))))
                .thenReturn(tokens);

        assertSame(tokens, ssoService.signIn(verified));
        verify(userIdentityRepository, never()).save(any());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenEmailMatchesExistingAccount_linksIdentity() {
        Credential existing = Credential.withPassword(userId, "ana@gmail.com", "$2a$hash");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.of(existing));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.USER))))
                .thenReturn(tokens);

        ssoService.signIn(verified);

        ArgumentCaptor<UserIdentity> saved = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals("google", saved.getValue().getProvider());
        assertEquals("sub-1", saved.getValue().getSubject());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenEmailNotVerified_rejectsWithoutLinkingOrCreating() {
        ExternalIdentity unverified =
                new ExternalIdentity("google", "sub-1", "ana@gmail.com", false, "Ana");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> ssoService.signIn(unverified));
        verify(credentialRepository, never()).findByEmail(any());
        verify(userIdentityRepository, never()).save(any());
        verify(userProvisioningService, never()).provisionWithoutPassword(any(), any(), any());
    }

    @Test
    void signIn_whenNewEmail_provisionsWithoutPasswordAndLinks() {
        User created = new User();
        created.setId(userId);
        created.setUsername("ana");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());
        when(usernameGenerator.generate("ana@gmail.com")).thenReturn("ana");
        when(userProvisioningService.provisionWithoutPassword("ana", "ana@gmail.com", "Ana Maria"))
                .thenReturn(created);
        when(credentialRepository.findById(userId))
                .thenReturn(Optional.of(Credential.ssoOnly(userId, "ana@gmail.com")));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), eq(Set.of(Role.USER))))
                .thenReturn(tokens);

        assertSame(tokens, ssoService.signIn(verified));
        verify(userIdentityRepository).save(any(UserIdentity.class));
        verify(userProvisioningService, never()).provisionWithPassword(any(), any(), any(), any());
    }

    @Test
    void signIn_whenProviderHasNoName_usesUsernameAsDisplayName() {
        ExternalIdentity noName = new ExternalIdentity("google", "sub-1", "ana@gmail.com", true, " ");
        User created = new User();
        created.setId(userId);
        created.setUsername("ana");
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());
        when(usernameGenerator.generate("ana@gmail.com")).thenReturn("ana");
        when(userProvisioningService.provisionWithoutPassword("ana", "ana@gmail.com", "ana"))
                .thenReturn(created);
        when(credentialRepository.findById(userId))
                .thenReturn(Optional.of(Credential.ssoOnly(userId, "ana@gmail.com")));
        when(wandererQueryClient.getUserById(userId, "basic"))
                .thenReturn(new UserBasicInfo(userId, "ana"));
        when(tokenService.issueLoginTokens(any(User.class), any())).thenReturn(tokens);

        ssoService.signIn(noName);

        verify(userProvisioningService).provisionWithoutPassword("ana", "ana@gmail.com", "ana");
    }

    @Test
    void signIn_whenAccountDisabled_rejects() {
        Credential disabled = Credential.ssoOnly(userId, "ana@gmail.com");
        disabled.setEnabled(false);
        when(userIdentityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(link()));
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(disabled));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ssoService.signIn(verified));
        assertEquals("Account disabled", ex.getMessage());
        verify(tokenService, never()).issueLoginTokens(any(), any());
    }

    private UserIdentity link() {
        return UserIdentity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("google")
                .subject("sub-1")
                .email("ana@gmail.com")
                .build();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -pl wanderer-auth -am test -Dtest=SsoServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class SsoServiceImpl`.

- [ ] **Step 3: Implement**

`SsoService.java`:

```java
package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;

/** Signs a user in from a verified external identity, linking or creating the account. */
public interface SsoService {

    /**
     * @throws IllegalArgumentException if the email is unverified or the account is disabled
     * @throws IllegalStateException if provisioning or user lookup fails
     */
    LoginResponse signIn(ExternalIdentity identity);
}
```

`SsoServiceImpl.java`:

```java
package com.tomassirio.wanderer.auth.service.impl;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.domain.UserIdentity;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.repository.UserIdentityRepository;
import com.tomassirio.wanderer.auth.service.SsoService;
import com.tomassirio.wanderer.auth.service.TokenService;
import com.tomassirio.wanderer.auth.service.UserProvisioningService;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;
import com.tomassirio.wanderer.auth.sso.UsernameGenerator;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsoServiceImpl implements SsoService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UserIdentityRepository userIdentityRepository;
    private final CredentialRepository credentialRepository;
    private final UserProvisioningService userProvisioningService;
    private final UsernameGenerator usernameGenerator;
    private final WandererQueryClient wandererQueryClient;
    private final TokenService tokenService;

    @Override
    public LoginResponse signIn(ExternalIdentity identity) {
        Credential credential =
                userIdentityRepository
                        .findByProviderAndSubject(identity.provider(), identity.subject())
                        .flatMap(link -> credentialRepository.findById(link.getUserId()))
                        .orElseGet(() -> linkOrProvision(identity));

        if (!credential.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }
        return tokenService.issueLoginTokens(
                loadUser(credential.getUserId()), credential.getRoles());
    }

    private Credential linkOrProvision(ExternalIdentity identity) {
        if (identity.email() == null || !identity.emailVerified()) {
            throw new IllegalArgumentException("SSO provider did not return a verified email");
        }
        Credential credential =
                credentialRepository
                        .findByEmail(identity.email())
                        .orElseGet(() -> provision(identity));

        userIdentityRepository.save(
                UserIdentity.builder()
                        .id(UUID.randomUUID())
                        .userId(credential.getUserId())
                        .provider(identity.provider())
                        .subject(identity.subject())
                        .email(identity.email())
                        .build());
        log.info("Linked {} identity to user {}", identity.provider(), credential.getUserId());
        return credential;
    }

    private Credential provision(ExternalIdentity identity) {
        String username = usernameGenerator.generate(identity.email());
        User user =
                userProvisioningService.provisionWithoutPassword(
                        username, identity.email(), displayName(identity, username));
        return credentialRepository
                .findById(user.getId())
                .orElseThrow(
                        () -> new IllegalStateException("Credential missing after provisioning"));
    }

    private static String displayName(ExternalIdentity identity, String fallback) {
        String name = identity.name();
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return name.length() > MAX_DISPLAY_NAME_LENGTH
                ? name.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : name;
    }

    private User loadUser(UUID userId) {
        try {
            UserBasicInfo info = wandererQueryClient.getUserById(userId, "basic");
            User user = new User();
            user.setId(info.id());
            user.setUsername(info.username());
            return user;
        } catch (FeignException e) {
            throw new IllegalStateException("Failed to fetch user information", e);
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `mvn -pl wanderer-auth -am test -Dtest=SsoServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/SsoService.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/service/impl/SsoServiceImpl.java wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/service/SsoServiceImplTest.java
git commit -m "feat(auth): sign in, link or create users from SSO identities"
```

---

### Task 7: One-time login code (Redis) and the `/sso/exchange` endpoint

**Files:**
- Modify: `commons/src/main/java/com/tomassirio/wanderer/commons/constants/ApiConstants.java` (Auth section)
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/config/SsoProperties.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/config/SsoConfig.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/SsoLoginCodeStore.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/dto/SsoExchangeRequest.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/controller/SsoController.java`
- Modify: `wanderer-auth/src/main/resources/application.properties`
- Modify: `wanderer-auth/src/test/resources/application.properties`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/SsoLoginCodeStoreTest.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/controller/SsoControllerTest.java`

**Interfaces:**
- Produces:
  - `ApiConstants.SSO_EXCHANGE_ENDPOINT = "/sso/exchange"`, `ApiConstants.SSO_AUTHORIZATION_BASE_URI = AUTH_PATH + "/oauth2/authorization"`, `ApiConstants.SSO_CALLBACK_BASE_URI = AUTH_PATH + "/oauth2/callback"`.
  - `record SsoProperties(List<String> allowedReturnUris, Duration loginCodeTtl)` with `String defaultReturnUri()`.
  - `String SsoLoginCodeStore.store(LoginResponse)`, `Optional<LoginResponse> SsoLoginCodeStore.consume(String code)` (single use).

- [ ] **Step 1: Write the failing tests**

`SsoLoginCodeStoreTest.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.auth.config.SsoProperties;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SsoLoginCodeStoreTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;

    private SsoLoginCodeStore store;
    private final LoginResponse login =
            new LoginResponse("access", "refresh", "Bearer", 900000L, "ana");

    @BeforeEach
    void setUp() {
        store =
                new SsoLoginCodeStore(
                        redis,
                        new ObjectMapper(),
                        new SsoProperties(
                                List.of("wanderer://auth/sso-callback"), Duration.ofSeconds(60)));
    }

    @Test
    void store_writesJsonWithTtlUnderRandomCode() {
        when(redis.opsForValue()).thenReturn(ops);

        String first = store.store(login);
        String second = store.store(login);

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43, "256-bit url-safe code expected");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ops)
                .set(eq(SsoLoginCodeStore.KEY_PREFIX + first), json.capture(), eq(Duration.ofSeconds(60)));
        assertTrue(json.getValue().contains("\"accessToken\":\"access\""));
    }

    @Test
    void consume_returnsResponseAndDeletesAtomically() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(SsoLoginCodeStore.KEY_PREFIX + "abc"))
                .thenReturn(
                        "{\"accessToken\":\"access\",\"refreshToken\":\"refresh\","
                                + "\"tokenType\":\"Bearer\",\"expiresIn\":900000,\"username\":\"ana\"}");

        assertEquals(login, store.consume("abc").orElseThrow());
    }

    @Test
    void consume_unknownCode_isEmpty() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(anyString())).thenReturn(null);

        assertTrue(store.consume("nope").isEmpty());
    }
}
```

`SsoControllerTest.java`:

```java
package com.tomassirio.wanderer.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.sso.SsoLoginCodeStore;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SsoControllerTest {

    private static final String URL = "/api/1/auth/sso/exchange";

    @Mock private SsoLoginCodeStore ssoLoginCodeStore;
    @InjectMocks private SsoController ssoController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(ssoController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void exchange_validCode_returnsLoginResponse() throws Exception {
        when(ssoLoginCodeStore.consume("good"))
                .thenReturn(
                        Optional.of(
                                new LoginResponse("access", "refresh", "Bearer", 900000L, "ana")));

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void exchange_unknownCode_returns400() throws Exception {
        when(ssoLoginCodeStore.consume("bad")).thenReturn(Optional.empty());

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchange_blankCode_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -pl wanderer-auth -am test -Dtest='SsoLoginCodeStoreTest,SsoControllerTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class SsoLoginCodeStore`.

- [ ] **Step 3: Implement**

In `ApiConstants.java`, after `PASSWORD_CHANGE_ENDPOINT`:

```java
    public static final String SSO_EXCHANGE_ENDPOINT = "/sso/exchange";
    public static final String SSO_AUTHORIZATION_BASE_URI = AUTH_PATH + "/oauth2/authorization";
    public static final String SSO_CALLBACK_BASE_URI = AUTH_PATH + "/oauth2/callback";
```

`SsoProperties.java`:

```java
package com.tomassirio.wanderer.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedReturnUris exact URIs the SSO flow may redirect back to; the first is the default
 * @param loginCodeTtl lifetime of the one-time code handed to the client
 */
@ConfigurationProperties(prefix = "app.sso")
public record SsoProperties(List<String> allowedReturnUris, Duration loginCodeTtl) {

    public SsoProperties {
        if (allowedReturnUris == null || allowedReturnUris.isEmpty()) {
            throw new IllegalStateException("app.sso.allowed-return-uris must list at least one URI");
        }
        if (loginCodeTtl == null) {
            loginCodeTtl = Duration.ofSeconds(60);
        }
    }

    public String defaultReturnUri() {
        return allowedReturnUris.get(0);
    }
}
```

`SsoConfig.java`:

```java
package com.tomassirio.wanderer.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfig {}
```

`SsoLoginCodeStore.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.auth.config.SsoProperties;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Hands SSO results to the client without putting tokens in a URL: the redirect carries a random
 * single-use code, the client swaps it for the LoginResponse via POST.
 */
@Component
@RequiredArgsConstructor
public class SsoLoginCodeStore {

    static final String KEY_PREFIX = "wanderer:auth:sso-code:";
    private static final int CODE_BYTES = 32;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SsoProperties ssoProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String store(LoginResponse response) {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + code, write(response), ssoProperties.loginCodeTtl());
        return code;
    }

    public Optional<LoginResponse> consume(String code) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + code))
                .map(this::read);
    }

    private String write(LoginResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SSO login", e);
        }
    }

    private LoginResponse read(String json) {
        try {
            return objectMapper.readValue(json, LoginResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize SSO login", e);
        }
    }
}
```

`SsoExchangeRequest.java`:

```java
package com.tomassirio.wanderer.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoExchangeRequest(@NotBlank(message = "Code is required") String code) {}
```

`SsoController.java`:

```java
package com.tomassirio.wanderer.auth.controller;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.dto.SsoExchangeRequest;
import com.tomassirio.wanderer.auth.sso.SsoLoginCodeStore;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO completion endpoint. Starting a login is a browser navigation to {@code
 * /api/1/auth/oauth2/authorization/{provider}?return_to=...}, handled by Spring Security.
 */
@RestController
@RequestMapping(value = ApiConstants.AUTH_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SSO", description = "Single sign-on login completion")
public class SsoController {

    private final SsoLoginCodeStore ssoLoginCodeStore;

    @PostMapping(
            value = ApiConstants.SSO_EXCHANGE_ENDPOINT,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Exchange SSO login code",
            description =
                    "Exchanges the one-time code from the SSO redirect for access and refresh"
                            + " tokens. Each code works once and expires quickly.")
    public ResponseEntity<LoginResponse> exchange(@Valid @RequestBody SsoExchangeRequest request) {
        LoginResponse response =
                ssoLoginCodeStore
                        .consume(request.code())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Invalid or expired SSO code"));
        log.info("SSO code exchanged");
        return ResponseEntity.ok(response);
    }
}
```

Append to **both** `wanderer-auth/src/main/resources/application.properties` and `wanderer-auth/src/test/resources/application.properties`:

```properties
# SSO: exact URIs the flow may redirect back to (first = default/fallback) and one-time code TTL
app.sso.allowed-return-uris=${SSO_ALLOWED_RETURN_URIS:http://localhost:3000/auth/sso-callback,wanderer://auth/sso-callback}
app.sso.login-code-ttl=60s
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `mvn -pl wanderer-auth -am test -Dtest='SsoLoginCodeStoreTest,SsoControllerTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
mvn spotless:apply
git add commons/src/main/java/com/tomassirio/wanderer/commons/constants/ApiConstants.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/config/SsoProperties.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/config/SsoConfig.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/SsoLoginCodeStore.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/dto/SsoExchangeRequest.java wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/controller/SsoController.java wanderer-auth/src/main/resources/application.properties wanderer-auth/src/test/resources/application.properties wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/SsoLoginCodeStoreTest.java wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/controller/SsoControllerTest.java
git commit -m "feat(auth): add one-time SSO login code exchange"
```

---

### Task 8: Spring Session Redis, `oauth2Login` chain, return_to and handlers

**Files:**
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/SsoReturnUris.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/ReturnToAuthorizationRequestResolver.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/SsoAuthenticationSuccessHandler.java`
- Create: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso/SsoAuthenticationFailureHandler.java`
- Modify: `wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/config/SecurityConfig.java`
- Modify: `wanderer-auth/src/main/resources/application.properties`
- Modify: `wanderer-auth/src/test/resources/application.properties`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/SsoReturnUrisTest.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/SsoAuthenticationHandlersTest.java`
- Test: `wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso/SsoLoginFlowIT.java`

**Interfaces:**
- Consumes: `SsoProperties`, `SsoLoginCodeStore`, `ApiConstants.SSO_*` (Task 7); `SsoService.signIn` (Task 6); `SsoIdentityMapper` beans (Task 4).
- Produces:
  - `SsoReturnUris.resolve(String): String`, `remember(HttpServletRequest, String)`, `consume(HttpServletRequest): String` (reads the value, then invalidates the session), and `static withParam(String uri, String name, String value): String`.
  - `ReturnToAuthorizationRequestResolver.RETURN_TO_PARAMETER = "return_to"`.
  - The redirect contract: success → `<return_to>?code=<code>`; any failure → `<return_to>?error=sso_failed`.

- [ ] **Step 1: Write the failing unit tests**

`SsoReturnUrisTest.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class SsoReturnUrisTest {

    private static final String WEB = "http://localhost:3000/auth/sso-callback";
    private static final String MOBILE = "wanderer://auth/sso-callback";

    private final SsoReturnUris returnUris =
            new SsoReturnUris(new SsoProperties(List.of(WEB, MOBILE), Duration.ofSeconds(60)));

    @Test
    void resolve_keepsAllowedUri() {
        assertEquals(MOBILE, returnUris.resolve(MOBILE));
    }

    @Test
    void resolve_fallsBackToDefaultForUnknownOrMissing() {
        assertEquals(WEB, returnUris.resolve("https://evil.example/steal"));
        assertEquals(WEB, returnUris.resolve(MOBILE + "/extra"));
        assertEquals(WEB, returnUris.resolve(null));
    }

    @Test
    void rememberThenConsume_roundTripsAndInvalidatesSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        returnUris.remember(request, MOBILE);
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        assertEquals(MOBILE, returnUris.consume(request));
        assertTrue(session.isInvalid());
    }

    @Test
    void consume_withoutSession_returnsDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(WEB, returnUris.consume(request));
        assertNull(request.getSession(false));
    }

    @Test
    void withParam_appendsQueryParameter() {
        assertEquals(MOBILE + "?code=abc", SsoReturnUris.withParam(MOBILE, "code", "abc"));
    }
}
```

`SsoAuthenticationHandlersTest.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.service.SsoService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

@ExtendWith(MockitoExtension.class)
class SsoAuthenticationHandlersTest {

    private static final String WEB = "http://localhost:3000/auth/sso-callback";
    private static final String MOBILE = "wanderer://auth/sso-callback";

    @Mock private SsoService ssoService;
    @Mock private SsoLoginCodeStore codeStore;

    private SsoReturnUris returnUris;
    private SsoAuthenticationSuccessHandler successHandler;
    private SsoAuthenticationFailureHandler failureHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        returnUris =
                new SsoReturnUris(new SsoProperties(List.of(WEB, MOBILE), Duration.ofSeconds(60)));
        successHandler =
                new SsoAuthenticationSuccessHandler(
                        List.of(new GoogleSsoIdentityMapper()), ssoService, codeStore, returnUris);
        failureHandler = new SsoAuthenticationFailureHandler(returnUris);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        returnUris.remember(request, MOBILE);
    }

    @Test
    void success_redirectsToReturnUriWithCode() throws Exception {
        LoginResponse login = new LoginResponse("a", "r", "Bearer", 1L, "ana");
        when(ssoService.signIn(any())).thenReturn(login);
        when(codeStore.store(login)).thenReturn("abc");

        successHandler.onAuthenticationSuccess(request, response, token("google"));

        assertEquals(MOBILE + "?code=abc", response.getRedirectedUrl());
    }

    @Test
    void success_whenSignInFails_redirectsWithError() throws Exception {
        when(ssoService.signIn(any())).thenThrow(new IllegalArgumentException("Account disabled"));

        successHandler.onAuthenticationSuccess(request, response, token("google"));

        assertEquals(MOBILE + "?error=sso_failed", response.getRedirectedUrl());
        verify(codeStore, never()).store(any());
    }

    @Test
    void success_whenProviderHasNoMapper_redirectsWithError() throws Exception {
        successHandler.onAuthenticationSuccess(request, response, token("github"));

        assertEquals(MOBILE + "?error=sso_failed", response.getRedirectedUrl());
        verify(ssoService, never()).signIn(any());
    }

    @Test
    void failure_redirectsWithError() throws Exception {
        failureHandler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertEquals(MOBILE + "?error=sso_failed", response.getRedirectedUrl());
    }

    private OAuth2AuthenticationToken token(String registrationId) {
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("t")
                        .subject("sub-1")
                        .claim("email", "ana@gmail.com")
                        .claim("email_verified", true)
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        return new OAuth2AuthenticationToken(
                new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken),
                AuthorityUtils.NO_AUTHORITIES,
                registrationId);
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -pl wanderer-auth -am test -Dtest='SsoReturnUrisTest,SsoAuthenticationHandlersTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: compilation FAIL — `cannot find symbol: class SsoReturnUris`.

- [ ] **Step 3: Implement the return-URI helper, resolver and handlers**

`SsoReturnUris.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.config.SsoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Exact-match allowlist for where the SSO flow may send the user back. Never an open redirect. */
@Component
@RequiredArgsConstructor
public class SsoReturnUris {

    static final String SESSION_ATTRIBUTE = "wanderer.sso.return_to";

    private final SsoProperties ssoProperties;

    public String resolve(String requested) {
        return requested != null && ssoProperties.allowedReturnUris().contains(requested)
                ? requested
                : ssoProperties.defaultReturnUri();
    }

    public void remember(HttpServletRequest request, String requested) {
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, resolve(requested));
    }

    /** Reads the remembered URI and ends the handshake session (deletes it from Redis). */
    public String consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ssoProperties.defaultReturnUri();
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        session.invalidate();
        return value instanceof String uri ? resolve(uri) : ssoProperties.defaultReturnUri();
    }

    public static String withParam(String uri, String name, String value) {
        return UriComponentsBuilder.fromUriString(uri).queryParam(name, value).build().toUriString();
    }
}
```

`ReturnToAuthorizationRequestResolver.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Default resolver + PKCE, and remembers the client's return_to in the handshake session. */
public class ReturnToAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String RETURN_TO_PARAMETER = "return_to";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final SsoReturnUris returnUris;

    public ReturnToAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository, SsoReturnUris returnUris) {
        this.delegate =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, ApiConstants.SSO_AUTHORIZATION_BASE_URI);
        this.delegate.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        this.returnUris = returnUris;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest remember(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            returnUris.remember(request, request.getParameter(RETURN_TO_PARAMETER));
        }
        return authorizationRequest;
    }
}
```

`SsoAuthenticationSuccessHandler.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.service.SsoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Maps the provider user via its strategy, signs in, and redirects back with a one-time code. */
@Component
@Slf4j
public class SsoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final Map<String, SsoIdentityMapper> mappers;
    private final SsoService ssoService;
    private final SsoLoginCodeStore codeStore;
    private final SsoReturnUris returnUris;

    public SsoAuthenticationSuccessHandler(
            List<SsoIdentityMapper> mappers,
            SsoService ssoService,
            SsoLoginCodeStore codeStore,
            SsoReturnUris returnUris) {
        this.mappers =
                mappers.stream()
                        .collect(Collectors.toMap(SsoIdentityMapper::provider, Function.identity()));
        this.ssoService = ssoService;
        this.codeStore = codeStore;
        this.returnUris = returnUris;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        String returnTo = returnUris.consume(request);
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        try {
            SsoIdentityMapper mapper = mappers.get(provider);
            if (mapper == null) {
                throw new IllegalArgumentException("Unsupported SSO provider: " + provider);
            }
            String code = codeStore.store(ssoService.signIn(mapper.map(token.getPrincipal())));
            log.info("SSO login succeeded via {}", provider);
            response.sendRedirect(SsoReturnUris.withParam(returnTo, "code", code));
        } catch (RuntimeException e) {
            log.warn("SSO login via {} failed: {}", provider, e.getMessage());
            response.sendRedirect(SsoReturnUris.withParam(returnTo, "error", "sso_failed"));
        }
    }
}
```

`SsoAuthenticationFailureHandler.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** User cancelled, state mismatch, expired session, invalid ID token, etc. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SsoAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final SsoReturnUris returnUris;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        log.warn("SSO authentication failed: {}", exception.getMessage());
        response.sendRedirect(
                SsoReturnUris.withParam(returnUris.consume(request), "error", "sso_failed"));
    }
}
```

- [ ] **Step 4: Run the unit tests and confirm they pass**

Run: `mvn -pl wanderer-auth -am test -Dtest='SsoReturnUrisTest,SsoAuthenticationHandlersTest' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: all PASS.

- [ ] **Step 5: Write the failing integration test (real Redis and Postgres)**

`SsoLoginFlowIT.java`:

```java
package com.tomassirio.wanderer.auth.sso;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.auth.AuthApplication;
import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@Import(TestConfig.class)
@TestPropertySource(
        properties =
                "jwt.secret=test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum")
class SsoLoginFlowIT extends BaseIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (!redis.isRunning()) {
            redis.start();
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean private WandererCommandClient wandererCommandClient;
    @MockitoBean private WandererQueryClient wandererQueryClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private SsoLoginCodeStore codeStore;

    @Test
    void authorization_redirectsToGoogleWithPkceAndStoresSessionInRedis() throws Exception {
        mockMvc.perform(
                        get("/api/1/auth/oauth2/authorization/google")
                                .param("return_to", "wanderer://auth/sso-callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://accounts.google.com/")))
                .andExpect(header().string("Location", containsString("code_challenge=")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(
                        header().string(
                                        "Location",
                                        containsString(
                                                "redirect_uri=http://localhost/api/1/auth/oauth2/callback/google")))
                .andExpect(header().string("Set-Cookie", containsString("WANDERER_SSO_SESSION=")));

        assertFalse(redisTemplate.keys("wanderer:auth:session:sessions:*").isEmpty());
    }

    @Test
    void callback_withoutHandshakeSession_redirectsToDefaultWithError() throws Exception {
        mockMvc.perform(
                        get("/api/1/auth/oauth2/callback/google")
                                .param("code", "fake")
                                .param("state", "fake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string(
                                        "Location",
                                        "http://localhost:3000/auth/sso-callback?error=sso_failed"));
    }

    @Test
    void exchange_isSingleUseAndApiStaysSessionless() throws Exception {
        String code = codeStore.store(new LoginResponse("access", "refresh", "Bearer", 1L, "ana"));
        String body = "{\"code\":\"" + code + "\"}";

        mockMvc.perform(
                        post("/api/1/auth/sso/exchange")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(
                        post("/api/1/auth/sso/exchange")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 6: Run the integration test and confirm it fails**

Run: `mvn -pl wanderer-auth -am verify -Dit.test=SsoLoginFlowIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: FAIL. `GET /api/1/auth/oauth2/authorization/google` returns 401 or 404 instead of a 302 to Google, because no `oauth2Login` chain exists yet.

- [ ] **Step 7: Wire up the configuration and the security chain**

Append to `wanderer-auth/src/main/resources/application.properties`:

```properties
# SSO: Google via Spring Security oauth2Login (provider details come from CommonOAuth2Provider)
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:local-google-client-id}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET:local-google-client-secret}
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/api/1/auth/oauth2/callback/{registrationId}

# Spring Session in Redis: only the SSO redirect handshake creates a session
spring.session.redis.namespace=wanderer:auth:session
spring.session.timeout=10m
server.servlet.session.cookie.name=WANDERER_SSO_SESSION
server.servlet.session.cookie.http-only=true
# Lax (not Strict): the cookie must come back on the top-level redirect from accounts.google.com
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=${SESSION_COOKIE_SECURE:false}

# Build {baseUrl} from X-Forwarded-* set by nginx/ingress so the Google redirect_uri is the public URL.
# Safe because pods are only reachable through the ingress.
server.forward-headers-strategy=framework
```

Append to `wanderer-auth/src/test/resources/application.properties`:

```properties
spring.security.oauth2.client.registration.google.client-id=test-google-client-id
spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/api/1/auth/oauth2/callback/{registrationId}
spring.session.redis.namespace=wanderer:auth:session
server.servlet.session.cookie.name=WANDERER_SSO_SESSION
server.servlet.session.cookie.same-site=lax
```

In `SecurityConfig.java`, mark the existing `filterChain` bean `@Order(2)` and add the SSO chain. The full updated class:

```java
package com.tomassirio.wanderer.auth.config;

import com.tomassirio.wanderer.auth.security.JtiValidatingJwtConverter;
import com.tomassirio.wanderer.auth.sso.ReturnToAuthorizationRequestResolver;
import com.tomassirio.wanderer.auth.sso.SsoAuthenticationFailureHandler;
import com.tomassirio.wanderer.auth.sso.SsoAuthenticationSuccessHandler;
import com.tomassirio.wanderer.auth.sso.SsoReturnUris;
import com.tomassirio.wanderer.commons.config.JwtConfig;
import com.tomassirio.wanderer.commons.config.RateLimitConfig;
import com.tomassirio.wanderer.commons.config.SecurityCorsConfig;
import com.tomassirio.wanderer.commons.config.SecurityHeadersConfig;
import com.tomassirio.wanderer.commons.config.SecurityHeadersConfig.SecurityHeadersCustomizer;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Import({
    JwtConfig.class,
    SecurityCorsConfig.class,
    SecurityHeadersConfig.class,
    RateLimitConfig.class
})
public class SecurityConfig {

    private final JtiValidatingJwtConverter jtiValidatingJwtConverter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final SecurityHeadersCustomizer securityHeadersCustomizer;

    /**
     * SSO redirect handshake. The only chain that may create an HTTP session (stored in Redis by
     * Spring Session) — it holds OAuth state/nonce/PKCE and return_to for a few minutes.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain ssoFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            SsoReturnUris ssoReturnUris,
            SsoAuthenticationSuccessHandler successHandler,
            SsoAuthenticationFailureHandler failureHandler)
            throws Exception {
        http.securityMatcher(ApiConstants.AUTH_PATH + "/oauth2/**")
                // Callback is protected by the OAuth state parameter, not CSRF tokens
                .csrf(AbstractHttpConfigurer::disable)
                .headers(securityHeadersCustomizer::configure)
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Never keep the Spring SecurityContext in the session: we hand out JWTs instead
                .securityContext(
                        context ->
                                context.securityContextRepository(
                                        new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .oauth2Login(
                        oauth2 ->
                                oauth2.authorizationEndpoint(
                                                endpoint ->
                                                        endpoint.baseUri(
                                                                        ApiConstants
                                                                                .SSO_AUTHORIZATION_BASE_URI)
                                                                .authorizationRequestResolver(
                                                                        new ReturnToAuthorizationRequestResolver(
                                                                                clientRegistrationRepository,
                                                                                ssoReturnUris)))
                                        .redirectionEndpoint(
                                                endpoint ->
                                                        endpoint.baseUri(
                                                                ApiConstants.SSO_CALLBACK_BASE_URI
                                                                        + "/*"))
                                        .successHandler(successHandler)
                                        .failureHandler(failureHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(securityHeadersCustomizer::configure)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authz ->
                                authz.requestMatchers("/api/1/auth/**")
                                        .permitAll()
                                        .requestMatchers("/assets/**")
                                        .permitAll()
                                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jtiValidatingJwtConverter)));
        return http.build();
    }
}
```

- [ ] **Step 8: Run the integration test and confirm it passes**

Run: `mvn -pl wanderer-auth -am verify -Dit.test=SsoLoginFlowIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`
Expected: `Tests run: 3, Failures: 0`.

If `callback_withoutHandshakeSession...` returns 401 or 404 instead of a redirect, check that the callback path matches `redirectionEndpoint.baseUri` exactly (`/api/1/auth/oauth2/callback/*`).

- [ ] **Step 9: Run the full auth suite, including Cucumber, to check for regressions**

Run: `mvn -pl wanderer-auth -am clean verify`
Expected: BUILD SUCCESS, with the JaCoCo check passing (≥ 80%).

- [ ] **Step 10: Commit**

```bash
mvn spotless:apply
git add wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/sso wanderer-auth/src/main/java/com/tomassirio/wanderer/auth/config/SecurityConfig.java wanderer-auth/src/main/resources/application.properties wanderer-auth/src/test/resources/application.properties wanderer-auth/src/test/java/com/tomassirio/wanderer/auth/sso
git commit -m "feat(auth): Google SSO via oauth2Login with Redis-backed handshake session"
```

---

### Task 9: Deployment wiring and operations docs

**Files:**
- Modify: `docker-compose.yml` (`wanderer-auth.environment`)
- Modify: `wanderer-auth/src/main/chart/values.yaml` (under `application:`)
- Modify: `wanderer-auth/src/main/chart/templates/configmap.yaml`
- Modify: `.github/workflows/helm-deploy.yml` (the `wanderer-auth` block around line 241)
- Modify (other repo): `../wanderer-frontend/docker/nginx/nginx.conf`
- Create: `docs/SSO.md`

- [ ] **Step 1: docker-compose.** Add to `wanderer-auth.environment`:

```yaml
      # SSO (Google). Register http://localhost:8083/api/1/auth/oauth2/callback/google in Google Cloud Console
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-local-google-client-id}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-local-google-client-secret}
      SSO_ALLOWED_RETURN_URIS: ${SSO_ALLOWED_RETURN_URIS:-http://localhost:51538/auth/sso-callback,wanderer://auth/sso-callback}
      SESSION_COOKIE_SECURE: "false"
```

- [ ] **Step 2: Helm values.** Under `application:` in `values.yaml`, after the `email:` block:

```yaml
  sso:
    google:
      clientId: ""      # Set via GitHub variable GOOGLE_CLIENT_ID (startup fails if empty)
      clientSecret: ""  # Set via GitHub secret GOOGLE_CLIENT_SECRET
    allowedReturnUris: "https://wanderer.localwanderer-dev.com/auth/sso-callback,wanderer://auth/sso-callback"
    loginCodeTtl: "60s"
  session:
    cookieSecure: true
```

- [ ] **Step 3: Helm configmap.** Append inside the `application.properties: |` block of `configmap.yaml`:

```
    # SSO (Google via oauth2Login)
    spring.security.oauth2.client.registration.google.client-id={{ .Values.application.sso.google.clientId }}
    spring.security.oauth2.client.registration.google.client-secret={{ .Values.application.sso.google.clientSecret }}
    spring.security.oauth2.client.registration.google.scope=openid,email,profile
    spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/api/1/auth/oauth2/callback/{registrationId}
    app.sso.allowed-return-uris={{ .Values.application.sso.allowedReturnUris }}
    app.sso.login-code-ttl={{ .Values.application.sso.loginCodeTtl }}

    # Spring Session (Redis) for the SSO handshake only
    spring.session.redis.namespace=wanderer:auth:session
    spring.session.timeout=10m
    server.servlet.session.cookie.name=WANDERER_SSO_SESSION
    server.servlet.session.cookie.http-only=true
    server.servlet.session.cookie.same-site=lax
    server.servlet.session.cookie.secure={{ .Values.application.session.cookieSecure }}
    server.forward-headers-strategy=framework
```

- [ ] **Step 4: Deploy workflow.** Inside `if [ "${{ matrix.service }}" == "wanderer-auth" ]; then` (line 241), after the bootstrap admin block, add the following. Helm's `--set` splits on commas, so they are escaped the same way as `CORS_ORIGINS_ESCAPED`:

```bash
            # SSO (Google) from GitHub environment variables and secrets
            SSO_RETURN_URIS="${{ vars.SSO_ALLOWED_RETURN_URIS }}"
            HELM_ARGS+=(
              "--set-string" "application.sso.google.clientId=${{ vars.GOOGLE_CLIENT_ID }}"
              "--set-string" "application.sso.google.clientSecret=${{ secrets.GOOGLE_CLIENT_SECRET }}"
            )
            if [ -n "$SSO_RETURN_URIS" ]; then
              HELM_ARGS+=(
                "--set-string" "application.sso.allowedReturnUris=${SSO_RETURN_URIS//,/\\,}"
              )
            fi
```

- [ ] **Step 5: nginx (`wanderer-frontend` repo).** Add this before `location /api/auth/`. It forwards the path **unchanged**, so the URL Spring sees matches the `redirect_uri` registered with Google. It also forwards the ingress's `X-Forwarded-Proto` (`https`) instead of nginx's own `$scheme` (`http`):

```nginx
    # SSO handshake - path kept as-is (no /api/auth → /api/1/auth rewrite) so the OAuth redirect_uri matches
    location /api/1/auth/oauth2/ {
        set $backend_auth wanderer-auth:8083;
        proxy_pass http://$backend_auth;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
        proxy_set_header X-Forwarded-Host $host;
    }
```

Commit it in the frontend repo: `git -C ../wanderer-frontend commit -am "feat(nginx): route SSO handshake to wanderer-auth unchanged"`.

- [ ] **Step 6: `docs/SSO.md`.** Create it with:

```markdown
# SSO (Google)

## How it works
1. Client navigates to `/api/1/auth/oauth2/authorization/google?return_to=<allowed uri>`.
2. `wanderer-auth` stores OAuth state + `return_to` in a Redis-backed session (`WANDERER_SSO_SESSION` cookie, 10 min) and redirects to Google.
3. Google redirects to `/api/1/auth/oauth2/callback/google`; Spring validates the ID token.
4. `SsoIdentityMapper` (strategy per provider) → `SsoService.signIn` → find / link (verified email only) / create.
5. Redirect to `return_to?code=<one-time code>` (60 s, single use) or `return_to?error=sso_failed`.
6. Client `POST /api/auth/sso/exchange {"code": "..."}` → same `LoginResponse` as `/login`.

## Google Cloud Console
- OAuth consent screen: scopes `openid`, `email`, `profile`.
- Create a **Web application** OAuth client. Authorized redirect URIs:
  - `http://localhost:8083/api/1/auth/oauth2/callback/google` (local)
  - `https://<dev host>/api/1/auth/oauth2/callback/google`
  - `https://<prod host>/api/1/auth/oauth2/callback/google`
- Mobile uses the same web client (system browser + `wanderer://` deep link); no Android/iOS client IDs needed.

## Configuration
| Where | Name | Value |
|---|---|---|
| GitHub variable | `GOOGLE_CLIENT_ID` | web client id |
| GitHub secret | `GOOGLE_CLIENT_SECRET` | web client secret |
| GitHub variable | `SSO_ALLOWED_RETURN_URIS` | comma-separated, exact match, first = fallback |

Startup fails if the client id is empty. Set the variables before deploying.

## Adding a provider
1. `spring.security.oauth2.client.registration.<id>.*` (+ `provider.<id>.*` if not built into Spring).
2. A `@Component` implementing `SsoIdentityMapper` with `provider()` = `<id>`.
3. Register `https://<host>/api/1/auth/oauth2/callback/<id>` with the provider.
Non-OIDC providers (e.g. GitHub) may not return a verified email in the user attributes; the mapper must fetch it or report `emailVerified=false`, which blocks auto-linking and sign-up.
```

- [ ] **Step 7: Validate the Helm templates**

Run: `helm template wanderer-auth wanderer-auth/src/main/chart --set application.sso.google.clientId=x | grep -A2 "oauth2.client.registration.google.client-id"`
Expected: `...client-id=x`.

- [ ] **Step 8: Commit**

```bash
git add docker-compose.yml wanderer-auth/src/main/chart .github/workflows/helm-deploy.yml docs/SSO.md
git commit -m "chore(auth): wire Google SSO config into compose, helm and deploy workflow"
```

---

### Task 10: End-to-end check against real Google (manual)

- [ ] **Step 1:** Create the Google OAuth web client following `docs/SSO.md`, then run `export GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=...`.
- [ ] **Step 2:** `mvn clean install -DskipTests && docker compose up -d`.
- [ ] **Step 3:** In a browser, open `http://localhost:8083/api/1/auth/oauth2/authorization/google?return_to=http://localhost:51538/auth/sso-callback`. Expected: the Google consent screen.
- [ ] **Step 4:** Sign in. Expected: the browser lands on `http://localhost:51538/auth/sso-callback?code=...`. (A frontend 404 is fine until the frontend plan ships.)
- [ ] **Step 5:** `curl -s -X POST localhost:8083/api/1/auth/sso/exchange -H 'Content-Type: application/json' -d '{"code":"<code>"}'`. Expected: a JSON `LoginResponse`. Running the same command again returns 400.
- [ ] **Step 6:** Check Redis: `docker exec wanderer-redis redis-cli --scan --pattern 'wanderer:auth:session:*'`. Expected: empty (the session was invalidated after the callback).
- [ ] **Step 7:** Repeat with an email that already has a password account. Expected: the same `userId` in the JWT `sub`, and the password login still works.
- [ ] **Step 8:** Register a new user with a password through `/register` and `/verify-email`. Expected: this still works, and `SELECT password_hash FROM user_credentials WHERE email = '<email>'` is not null.
- [ ] **Step 9:** Open the authorization URL, click "Cancel" on Google. Expected: redirect to `...?error=sso_failed`.
- [ ] **Step 10:** `mvn clean verify` for the whole reactor. Expected: BUILD SUCCESS.

---

## Out of Scope (tracked separately)

- **Flutter frontend plan:** the Google button opens `…/api/1/auth/oauth2/authorization/google?return_to=…`. On web this is a full-page navigation to `/auth/sso-callback`. On mobile it goes through `flutter_web_auth_2` with callback scheme `wanderer`, then `POST /sso/exchange`, then the existing token storage.
- Unlinking providers and listing linked identities in the profile.
- Apple sign-in. Apple posts the callback as `form_post` and needs a JWT client secret, which calls for a custom client-authentication setup.

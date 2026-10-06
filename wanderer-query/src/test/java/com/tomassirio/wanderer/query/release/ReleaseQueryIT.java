package com.tomassirio.wanderer.query.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.domain.Release;
import com.tomassirio.wanderer.commons.domain.Release.Platform;
import com.tomassirio.wanderer.commons.domain.Release.PlatformRelease;
import com.tomassirio.wanderer.commons.domain.UserReleaseSeen;
import com.tomassirio.wanderer.commons.utils.JwtBuilder;
import com.tomassirio.wanderer.query.WandererQueryApplication;
import com.tomassirio.wanderer.query.client.WandererAuthClient;
import com.tomassirio.wanderer.query.repository.ReleaseRepository;
import com.tomassirio.wanderer.query.repository.UserReleaseSeenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;

/**
 * Release notes read side against a real Postgres. The query module has no migrations of its own,
 * so the schema is generated from the entities here (the command module's IT validates the
 * Liquibase schema against the same entities).
 */
@SpringBootTest(
        classes = WandererQueryApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
@TestPropertySource(
        properties = {
            "jwt.secret=" + ReleaseQueryIT.SECRET,
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.liquibase.enabled=false",
            "wanderer.auth.url=localhost:8083",
            "spring.cloud.compatibility-verifier.enabled=false"
        })
class ReleaseQueryIT extends BaseIntegrationTest {

    static final String SECRET =
            "test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant PAST = Instant.now().minus(Duration.ofDays(2));
    private static final Instant FUTURE = Instant.now().plus(Duration.ofDays(2));

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockitoBean private WandererAuthClient wandererAuthClient;

    @Autowired private TestRestTemplate rest;
    @Autowired private ReleaseRepository releaseRepository;
    @Autowired private UserReleaseSeenRepository seenRepository;

    @BeforeEach
    void clean() {
        releaseRepository.deleteAll();
        seenRepository.deleteAll();
    }

    // --- Visibility ---

    @Test
    void notes_onlyVisibleOncePublishedAndReleasedOnThatPlatform() {
        save("1.0.0", Release.Status.PUBLISHED, true, Map.of(Platform.ANDROID, PAST), FUTURE);
        save("1.1.0", Release.Status.DRAFT, true, Map.of(Platform.ANDROID, PAST), PAST);

        // Released on Android, scheduled on web.
        assertThat(get("/api/1/releases/1.0.0?platform=ANDROID", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/1/releases/1.0.0?platform=android", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/1/releases/1.0.0?platform=WEB", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // Draft is never public.
        assertThat(get("/api/1/releases/1.1.0?platform=ANDROID", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/1/releases/9.9.9?platform=ANDROID", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/1/releases/1.0.0?platform=IOS", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Once the web date passes, it shows up there too.
        releaseRepository.deleteAll();
        save("1.0.0", Release.Status.PUBLISHED, true, Map.of(), PAST);
        assertThat(get("/api/1/releases/1.0.0?platform=WEB", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void history_isNewestFirstAndOnlyVisible() {
        save("1.0.0", Release.Status.PUBLISHED, true, Map.of(), PAST.minus(Duration.ofDays(9)));
        save("1.1.0", Release.Status.PUBLISHED, true, Map.of(), PAST);
        save("1.2.0", Release.Status.PUBLISHED, true, Map.of(), FUTURE);
        save("1.3.0", Release.Status.DRAFT, true, Map.of(), PAST);

        Map<String, Object> page = json(get("/api/1/releases?platform=WEB&size=1", null));
        assertThat(page.get("totalElements")).isEqualTo(2);
        assertThat(versions(page)).containsExactly("1.1.0");

        page = json(get("/api/1/releases?platform=WEB&size=1&page=1", null));
        assertThat(versions(page)).containsExactly("1.0.0");
    }

    // --- Read state ---

    @Test
    void unread_isSharedAcrossClientsOfTheSameUser() {
        save("1.1.0", Release.Status.PUBLISHED, true, Map.of(), PAST);
        save("1.2.0", Release.Status.PUBLISHED, true, Map.of(), PAST);
        save("1.3.0", Release.Status.PUBLISHED, false, Map.of(), PAST); // popup off
        save("1.4.0", Release.Status.PUBLISHED, true, Map.of(), PAST); // newer than installed
        UUID userId = UUID.randomUUID();
        seenRepository.save(new UserReleaseSeen(userId, "1.0.0", null));

        Map<String, Object> android =
                json(
                        get(
                                "/api/1/releases/me/unread?platform=ANDROID&currentVersion=1.3.0",
                                userId));
        assertThat(android.get("lastSeenVersion")).isEqualTo("1.0.0");
        assertThat(versionsOf(android.get("releases"))).containsExactly("1.2.0", "1.1.0");

        // Android client marks it read (PUT /releases/me/seen on wanderer-command stores this row).
        seenRepository.save(new UserReleaseSeen(userId, "1.3.0", null));

        Map<String, Object> web =
                json(get("/api/1/releases/me/unread?platform=WEB&currentVersion=1.3.0", userId));
        assertThat(versionsOf(web.get("releases"))).isEmpty();
        // Reinstall on Android: same answer, server-side state.
        Map<String, Object> reinstall =
                json(
                        get(
                                "/api/1/releases/me/unread?platform=ANDROID&currentVersion=1.3.0",
                                userId));
        assertThat(versionsOf(reinstall.get("releases"))).isEmpty();
    }

    @Test
    void unread_brandNewUserSeesNoPopupForInstalledVersion() {
        save("1.2.0", Release.Status.PUBLISHED, true, Map.of(), PAST);
        Map<String, Object> body =
                json(
                        get(
                                "/api/1/releases/me/unread?platform=ANDROID&currentVersion=1.2.0",
                                UUID.randomUUID()));
        assertThat(body.get("lastSeenVersion")).isNull();
        assertThat(versionsOf(body.get("releases"))).isEmpty();
    }

    @Test
    void unread_requiresAuth() {
        assertThat(
                        get("/api/1/releases/me/unread?platform=ANDROID&currentVersion=1.0.0", null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Admin ---

    @Test
    void adminList_requiresAdminAndIncludesDrafts() {
        save("1.0.0", Release.Status.DRAFT, true, Map.of(), null);

        assertThat(get("/api/1/admin/releases", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/1/admin/releases", UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(versions(json(exchange("/api/1/admin/releases", token("ADMIN")))))
                .containsExactly("1.0.0");
        assertThat(exchange("/api/1/admin/releases/1.0.0", token("ADMIN")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/1/admin/releases/1.0.0", token("USER")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    /** Saves a release targeting ANDROID and WEB; {@code dates} overrides per platform. */
    private void save(
            String version,
            Release.Status status,
            boolean popup,
            Map<Platform, Instant> dates,
            Instant defaultDate) {
        Release r = new Release();
        r.setId(UUID.randomUUID());
        r.setVersion(version);
        r.setStatus(status);
        r.setHeadline("Headline " + version);
        r.setShowPopup(popup);
        for (Platform p : List.of(Platform.ANDROID, Platform.WEB)) {
            r.getPlatforms().add(new PlatformRelease(p, dates.getOrDefault(p, defaultDate)));
        }
        r.getItems().add(new Release.Item(Release.ItemType.NEW, "Item", "Text", 1));
        releaseRepository.save(r);
    }

    private ResponseEntity<String> get(String url, UUID userId) {
        return exchange(url, userId == null ? null : token(userId, "USER"));
    }

    private ResponseEntity<String> exchange(String url, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) headers.setBearerAuth(bearer);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static List<Object> versions(Map<String, Object> page) {
        return versionsOf(page.get("content"));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> versionsOf(Object releases) {
        return ((List<Map<String, Object>>) releases).stream().map(m -> m.get("version")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return MAPPER.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(response.getBody(), e);
        }
    }

    private static String token(String role) {
        return token(UUID.randomUUID(), role);
    }

    private static String token(UUID userId, String role) {
        try {
            return JwtBuilder.buildJwt(
                    Map.of("sub", userId.toString(), "roles", List.of(role)), SECRET);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

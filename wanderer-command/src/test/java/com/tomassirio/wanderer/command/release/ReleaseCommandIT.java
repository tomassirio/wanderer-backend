package com.tomassirio.wanderer.command.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.maps.GeoApiContext;
import com.tomassirio.wanderer.command.WandererCommandApplication;
import com.tomassirio.wanderer.command.client.WandererAuthClient;
import com.tomassirio.wanderer.command.repository.ReleaseRepository;
import com.tomassirio.wanderer.command.repository.UserReleaseSeenRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.BaseIntegrationTest;
import com.tomassirio.wanderer.commons.config.TestConfig;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.utils.JwtBuilder;
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

/** Release notes write side against a real Postgres with the Liquibase schema. */
@SpringBootTest(
        classes = WandererCommandApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
@TestPropertySource(
        properties = {
            "jwt.secret=" + ReleaseCommandIT.SECRET,
            "release.ci.token=" + ReleaseCommandIT.CI_TOKEN,
            "spring.jpa.hibernate.ddl-auto=validate",
            "wanderer.auth.url=localhost:8083",
            "spring.cloud.compatibility-verifier.enabled=false"
        })
class ReleaseCommandIT extends BaseIntegrationTest {

    static final String SECRET =
            "test-secret-that-is-long-enough-for-jwt-hmac-sha-algorithm-256-bits-minimum";
    static final String CI_TOKEN = "test-ci-token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @MockitoBean private GeoApiContext geoApiContext;

    @Autowired private TestRestTemplate rest;
    @Autowired private ReleaseRepository releaseRepository;
    @Autowired private UserReleaseSeenRepository seenRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void clean() {
        releaseRepository.deleteAll();
        seenRepository.deleteAll();
    }

    // --- CI draft creation ---

    @Test
    void draftCreation_isIdempotentPerVersion() {
        ResponseEntity<String> first =
                draft(
                        CI_TOKEN,
                        Map.of(
                                "version",
                                "2.0.0",
                                "prs",
                                List.of(pr(1, "Add map", "feature"), pr(2, "Fix crash", "bug"))));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(first).get("status")).isEqualTo("DRAFT");
        assertThat((List<?>) json(first).get("platforms")).hasSize(2);

        ResponseEntity<String> second =
                draft(
                        CI_TOKEN,
                        Map.of(
                                "version",
                                "2.0.0",
                                "prs",
                                List.of(pr(2, "Fix crash on start", "bug"), pr(3, "Tweak", "x"))));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(releaseRepository.count()).isEqualTo(1);
        List<Map<String, Object>> items = (List<Map<String, Object>>) json(second).get("items");
        assertThat(items)
                .extracting(i -> i.get("prNumber"), i -> i.get("type"), i -> i.get("title"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "NEW", "Add map"),
                        org.assertj.core.groups.Tuple.tuple(2, "FIXED", "Fix crash on start"),
                        org.assertj.core.groups.Tuple.tuple(3, "IMPROVED", "Tweak"));
    }

    @Test
    void draftCreation_rejectsMissingOrWrongToken() {
        Map<String, Object> body = Map.of("version", "2.0.0", "prs", List.of());
        assertThat(draft(null, body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(draft("nope", body).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(releaseRepository.count()).isZero();
    }

    @Test
    void draftCreation_conflictsOncePublished() {
        draft(CI_TOKEN, Map.of("version", "2.0.0", "prs", List.of(pr(1, "A", "feature"))));
        adminUpdate("2.0.0", adminToken());
        assertThat(
                        exchange(
                                        HttpMethod.POST,
                                        "/api/1/admin/releases/2.0.0/publish",
                                        null,
                                        adminToken())
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(draft(CI_TOKEN, Map.of("version", "2.0.0", "prs", List.of())).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void draftCreation_rejectsInvalidVersion() {
        assertThat(draft(CI_TOKEN, Map.of("version", "v2", "prs", List.of())).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Admin access ---

    @Test
    void adminEndpoints_requireAdminRole() {
        draft(CI_TOKEN, Map.of("version", "2.0.0", "prs", List.of()));

        assertThat(adminUpdate("2.0.0", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(adminUpdate("2.0.0", token(UUID.randomUUID(), "USER")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        exchange(
                                        HttpMethod.POST,
                                        "/api/1/admin/releases/2.0.0/publish",
                                        null,
                                        token(UUID.randomUUID(), "USER"))
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> updated = adminUpdate("2.0.0", adminToken());
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(updated).get("headline")).isEqualTo("Big one");

        ResponseEntity<String> published =
                exchange(
                        HttpMethod.POST, "/api/1/admin/releases/2.0.0/publish", null, adminToken());
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(published).get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_fillsMissingReleaseDatesAndKeepsSetOnes() {
        draft(CI_TOKEN, Map.of("version", "2.0.0", "prs", List.of()));
        Map<String, Object> platforms = new java.util.HashMap<>();
        platforms.put("platform", "WEB");
        platforms.put("releaseDate", null);
        exchange(
                HttpMethod.PUT,
                "/api/1/admin/releases/2.0.0",
                Map.of(
                        "headline",
                        "Big one",
                        "showPopup",
                        true,
                        "platforms",
                        List.of(
                                Map.of(
                                        "platform",
                                        "ANDROID",
                                        "releaseDate",
                                        "2030-01-01T00:00:00Z"),
                                platforms),
                        "items",
                        List.of()),
                adminToken());

        ResponseEntity<String> published =
                exchange(
                        HttpMethod.POST, "/api/1/admin/releases/2.0.0/publish", null, adminToken());

        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> dates =
                (List<Map<String, Object>>) json(published).get("platforms");
        assertThat(dates)
                .anySatisfy(
                        p -> {
                            assertThat(p.get("platform")).isEqualTo("ANDROID");
                            assertThat((String) p.get("releaseDate")).startsWith("2030-01-01");
                        })
                .anySatisfy(
                        p -> {
                            assertThat(p.get("platform")).isEqualTo("WEB");
                            assertThat(p.get("releaseDate")).isNotNull();
                        });
    }

    @Test
    void publish_requiresHeadline() {
        draft(CI_TOKEN, Map.of("version", "2.0.0", "prs", List.of()));
        assertThat(
                        exchange(
                                        HttpMethod.POST,
                                        "/api/1/admin/releases/2.0.0/publish",
                                        null,
                                        adminToken())
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminUpdate_unknownVersionCreatesDraft() {
        ResponseEntity<String> saved = adminUpdate("9.9.9", adminToken());

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(saved).get("version")).isEqualTo("9.9.9");
        assertThat(json(saved).get("status")).isEqualTo("DRAFT");
    }

    // --- Read tracking ---

    @Test
    void markSeen_isSharedPerUserAndOnlyMovesForward() {
        UUID userId = UUID.randomUUID();
        userRepository.save(User.builder().id(userId).username("u" + userId).build());
        String androidClient = token(userId, "USER");
        String webClient = token(userId, "USER");

        assertThat(json(seen("1.3.0", androidClient))).containsEntry("lastSeenVersion", "1.3.0");
        // Second client on an older build must not move it back.
        assertThat(json(seen("1.2.0", webClient))).containsEntry("lastSeenVersion", "1.3.0");
        assertThat(json(seen("1.10.0", webClient))).containsEntry("lastSeenVersion", "1.10.0");
        assertThat(seenRepository.findById(userId).orElseThrow().getLastSeenVersion())
                .isEqualTo("1.10.0");
        assertThat(seenRepository.count()).isEqualTo(1);

        assertThat(seen("latest", webClient).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(seen("1.0.0", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private static Map<String, Object> pr(int number, String title, String label) {
        return Map.of("number", number, "title", title, "label", label, "summary", title + "!");
    }

    private ResponseEntity<String> draft(String ciToken, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        if (ciToken != null) headers.set("X-Release-Token", ciToken);
        return rest.exchange(
                "/api/1/releases/drafts",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private ResponseEntity<String> adminUpdate(String version, String bearer) {
        Map<String, Object> body =
                Map.of(
                        "headline",
                        "Big one",
                        "showPopup",
                        true,
                        "platforms",
                        List.of(
                                Map.of(
                                        "platform",
                                        "ANDROID",
                                        "releaseDate",
                                        "2026-01-01T00:00:00Z")),
                        "items",
                        List.of(Map.of("type", "NEW", "title", "Thing", "text", "Does stuff")));
        return exchange(HttpMethod.PUT, "/api/1/admin/releases/" + version, body, bearer);
    }

    private ResponseEntity<String> seen(String version, String bearer) {
        return exchange(
                HttpMethod.PUT, "/api/1/releases/me/seen", Map.of("version", version), bearer);
    }

    private ResponseEntity<String> exchange(
            HttpMethod method, String url, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) headers.setBearerAuth(bearer);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private static Map<String, Object> json(ResponseEntity<String> response) {
        try {
            return MAPPER.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(response.getStatusCode() + " " + response.getBody(), e);
        }
    }

    private static String adminToken() {
        return token(UUID.randomUUID(), "ADMIN");
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

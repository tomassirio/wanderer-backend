package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.controller.request.ReleaseDraftRequest;
import com.tomassirio.wanderer.command.controller.request.ReleasePublishRequest;
import com.tomassirio.wanderer.command.controller.request.ReleaseSeenRequest;
import com.tomassirio.wanderer.command.service.ReleaseService;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Release notes ("What's new") writes: read tracking for users and draft creation for CI.
 *
 * @since 1.3.0
 */
@RestController
@RequestMapping(value = ApiConstants.RELEASES_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Releases", description = "Release notes read tracking and CI draft creation")
public class ReleaseController {

    public static final String CI_TOKEN_HEADER = "X-Release-Token";

    private final ReleaseService releaseService;

    @Value("${release.ci.token:}")
    private String ciToken;

    @PutMapping(ApiConstants.RELEASES_SEEN_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Mark release notes as seen",
            description =
                    "Stores the highest release version the current user has seen. Only moves"
                            + " forward; shared across all of the user's clients.")
    @ApiResponse(responseCode = "200", description = "Stored last-seen version")
    @ApiResponse(responseCode = "400", description = "Invalid version")
    public ResponseEntity<Map<String, String>> markSeen(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @Valid @RequestBody ReleaseSeenRequest request) {
        String lastSeen = releaseService.markSeen(userId, request.version());
        return ResponseEntity.ok(Map.of("lastSeenVersion", lastSeen));
    }

    @PostMapping(ApiConstants.RELEASE_DRAFTS_ENDPOINT)
    @Operation(
            summary = "Create or update a release draft (CI)",
            description =
                    "Called by CI when a release is tagged. Requires the "
                            + CI_TOKEN_HEADER
                            + " header. Idempotent per version: updates the existing draft.")
    @ApiResponse(responseCode = "201", description = "Draft created")
    @ApiResponse(responseCode = "200", description = "Existing draft updated")
    @ApiResponse(responseCode = "401", description = "Missing or wrong CI token")
    @ApiResponse(responseCode = "409", description = "Version already published")
    public ResponseEntity<ReleaseDTO> upsertDraft(
            @Parameter(hidden = true) @RequestHeader(value = CI_TOKEN_HEADER, required = false)
                    String token,
            @Valid @RequestBody ReleaseDraftRequest request) {
        if (!validToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        ReleaseService.DraftResult result = releaseService.upsertDraft(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.release());
    }

    @PostMapping(ApiConstants.RELEASE_PUBLISH_ENDPOINT)
    @Operation(
            summary = "Write and publish release notes (CI)",
            description =
                    "Called by CI after a release is deployed. Requires the "
                            + CI_TOKEN_HEADER
                            + " header. Creates or replaces the draft's headline and items, shows the"
                            + " popup and publishes. A published version is left alone.")
    @ApiResponse(responseCode = "200", description = "Release published")
    @ApiResponse(responseCode = "401", description = "Missing or wrong CI token")
    @ApiResponse(responseCode = "409", description = "Version already published")
    public ResponseEntity<ReleaseDTO> publish(
            @Parameter(hidden = true) @RequestHeader(value = CI_TOKEN_HEADER, required = false)
                    String token,
            @Valid @RequestBody ReleasePublishRequest request) {
        if (!validToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        log.info("CI publishing release {}", request.version());
        return ResponseEntity.ok(releaseService.publishFromCi(request));
    }

    private boolean validToken(String token) {
        return ciToken != null
                && !ciToken.isBlank()
                && token != null
                && MessageDigest.isEqual(
                        ciToken.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8));
    }
}

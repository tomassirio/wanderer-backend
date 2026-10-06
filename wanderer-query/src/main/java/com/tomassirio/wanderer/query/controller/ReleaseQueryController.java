package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import com.tomassirio.wanderer.commons.dto.UnreadReleasesDTO;
import com.tomassirio.wanderer.commons.security.CurrentUserId;
import com.tomassirio.wanderer.query.service.ReleaseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public release notes ("What's new") and the current user's unread releases.
 *
 * @since 1.3.0
 */
@RestController
@RequestMapping(value = ApiConstants.RELEASES_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Releases Query", description = "Release notes and What's new popup")
public class ReleaseQueryController {

    private final ReleaseQueryService releaseQueryService;

    @GetMapping(ApiConstants.RELEASE_BY_VERSION_ENDPOINT)
    @Operation(
            summary = "Get release notes for a version",
            description = "Only returned once that version is released on the given platform.")
    @ApiResponse(responseCode = "200", description = "Release notes")
    @ApiResponse(responseCode = "404", description = "Unknown or not yet released on platform")
    public ResponseEntity<ReleaseDTO> getRelease(
            @PathVariable String version, @RequestParam String platform) {
        return ResponseEntity.ok(releaseQueryService.getVisible(version, platform));
    }

    @GetMapping
    @Operation(
            summary = "Release history for a platform",
            description = "Released versions on the platform, newest first, paginated.")
    public ResponseEntity<Page<ReleaseDTO>> getHistory(
            @RequestParam String platform, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(releaseQueryService.getHistory(platform, pageable));
    }

    @GetMapping(ApiConstants.RELEASES_UNREAD_ENDPOINT)
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Operation(
            summary = "Unread releases for the current user",
            description =
                    "Releases to show in the What's new popup. Users without a last-seen version"
                            + " get an empty list (treated as seen up to currentVersion).")
    public ResponseEntity<UnreadReleasesDTO> getUnread(
            @Parameter(hidden = true) @CurrentUserId UUID userId,
            @RequestParam String platform,
            @RequestParam String currentVersion) {
        return ResponseEntity.ok(releaseQueryService.getUnread(userId, platform, currentVersion));
    }
}

package com.tomassirio.wanderer.command.controller;

import com.tomassirio.wanderer.command.controller.request.ReleaseUpdateRequest;
import com.tomassirio.wanderer.command.service.ReleaseService;
import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin editing and publishing of release notes. All endpoints require ADMIN role.
 *
 * @since 1.3.0
 */
@RestController
@RequestMapping(
        value = ApiConstants.ADMIN_RELEASES_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Releases", description = "Admin-only release notes editing")
public class AdminReleaseController {

    private final ReleaseService releaseService;

    @PutMapping(ApiConstants.RELEASE_BY_VERSION_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Edit a release",
            description =
                    "Replaces headline, popup flag, platforms (with release dates) and the ordered"
                            + " items.")
    @ApiResponse(responseCode = "200", description = "Release updated")
    @ApiResponse(responseCode = "404", description = "Unknown version")
    public ResponseEntity<ReleaseDTO> update(
            @PathVariable String version, @Valid @RequestBody ReleaseUpdateRequest request) {
        log.info("Admin updating release {}", version);
        return ResponseEntity.ok(releaseService.update(version, request));
    }

    @PostMapping(ApiConstants.ADMIN_RELEASE_PUBLISH_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a release", description = "Marks the release as published.")
    @ApiResponse(responseCode = "200", description = "Release published")
    @ApiResponse(responseCode = "400", description = "Missing headline or platforms")
    @ApiResponse(responseCode = "404", description = "Unknown version")
    public ResponseEntity<ReleaseDTO> publish(@PathVariable String version) {
        log.info("Admin publishing release {}", version);
        return ResponseEntity.ok(releaseService.publish(version));
    }
}

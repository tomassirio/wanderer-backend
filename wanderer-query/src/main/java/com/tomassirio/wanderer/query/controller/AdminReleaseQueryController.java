package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import com.tomassirio.wanderer.query.service.ReleaseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin listing of all releases, drafts included. All endpoints require ADMIN role.
 *
 * @since 1.3.0
 */
@RestController
@RequestMapping(
        value = ApiConstants.ADMIN_RELEASES_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Admin - Releases Query", description = "Admin-only release listing")
public class AdminReleaseQueryController {

    private final ReleaseQueryService releaseQueryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all releases", description = "Drafts and published, newest first.")
    public ResponseEntity<Page<ReleaseDTO>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(releaseQueryService.getAll(pageable));
    }

    @GetMapping(ApiConstants.RELEASE_BY_VERSION_ENDPOINT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a release in any state")
    @ApiResponse(responseCode = "404", description = "Unknown version")
    public ResponseEntity<ReleaseDTO> get(@PathVariable String version) {
        return ResponseEntity.ok(releaseQueryService.getByVersion(version));
    }
}

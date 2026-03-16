package com.tomassirio.wanderer.query.controller;

import com.tomassirio.wanderer.commons.constants.ApiConstants;
import com.tomassirio.wanderer.commons.dto.CommentDTO;
import com.tomassirio.wanderer.query.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for comment query operations.
 *
 * @since 0.3.0
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comment Queries", description = "Endpoints for retrieving comment information")
public class CommentController {

    private final CommentService commentService;

    @GetMapping(
            value = ApiConstants.COMMENTS_PATH + ApiConstants.COMMENT_BY_ID_ENDPOINT,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get comment by ID",
            description = "Retrieves a specific comment by its ID")
    public ResponseEntity<CommentDTO> getComment(@PathVariable UUID id) {
        log.info("Received request to retrieve comment: {}", id);

        CommentDTO comment = commentService.getComment(id);

        log.info("Successfully retrieved comment with ID: {}", comment.id());
        return ResponseEntity.ok(comment);
    }

    @GetMapping(
            value = ApiConstants.TRIPS_PATH + ApiConstants.TRIP_COMMENTS_ENDPOINT,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all comments for a trip",
            description =
                    "Retrieves top-level comments with their replies for a specific trip "
                            + "with pagination and sorting. Defaults to most recent first. "
                            + "Use query parameters: page, size, sort (e.g., sort=timestamp,desc)")
    public ResponseEntity<Page<CommentDTO>> getCommentsForTrip(
            @PathVariable UUID tripId,
            @Parameter(description = "Pagination and sorting parameters")
                    @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        log.info(
                "Received request to retrieve comments for trip: {}, page: {}, size: {}",
                tripId,
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<CommentDTO> comments = commentService.getCommentsForTrip(tripId, pageable);

        log.info(
                "Successfully retrieved {} comments for trip {} (page {} of {})",
                comments.getNumberOfElements(),
                tripId,
                comments.getNumber() + 1,
                comments.getTotalPages());
        return ResponseEntity.ok(comments);
    }
}

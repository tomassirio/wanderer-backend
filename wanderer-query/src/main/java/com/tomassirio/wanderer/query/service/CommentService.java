package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.commons.dto.CommentDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for querying comment data.
 *
 * @since 0.3.0
 */
public interface CommentService {

    /**
     * Retrieves a single comment by its unique identifier.
     *
     * @param commentId the UUID of the comment to retrieve
     * @return a {@link CommentDTO} containing the comment data
     * @throws jakarta.persistence.EntityNotFoundException if no comment exists with the given ID
     */
    CommentDTO getComment(UUID commentId);

    /**
     * Retrieves top-level comments for a trip with pagination and sorting support. Each comment
     * includes its replies.
     *
     * @param tripId the UUID of the trip
     * @param pageable pagination and sorting parameters
     * @return a page of {@link CommentDTO} objects representing top-level comments with their
     *     replies
     */
    Page<CommentDTO> getCommentsForTrip(UUID tripId, Pageable pageable);
}

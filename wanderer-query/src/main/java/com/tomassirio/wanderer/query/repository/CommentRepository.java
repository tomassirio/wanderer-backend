package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Comment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Find all top-level comments for a trip (comments with no parent). The replies will be fetched
     * via the Comment entity's relationships.
     */
    @Query(
            "SELECT DISTINCT c FROM Comment c LEFT JOIN FETCH c.user LEFT JOIN FETCH c.commentReactions cr LEFT JOIN FETCH cr.user WHERE c.trip.id = :tripId AND c.parentComment IS NULL ORDER BY c.timestamp DESC")
    List<Comment> findTopLevelCommentsByTripId(@Param("tripId") UUID tripId);

    /**
     * Find all top-level comments for a trip with pagination. The replies will be fetched via the
     * Comment entity's relationships.
     */
    @Query(
            value =
                    "SELECT DISTINCT c FROM Comment c LEFT JOIN FETCH c.user"
                            + " LEFT JOIN FETCH c.commentReactions cr LEFT JOIN FETCH cr.user"
                            + " WHERE c.trip.id = :tripId AND c.parentComment IS NULL",
            countQuery =
                    "SELECT COUNT(DISTINCT c) FROM Comment c"
                            + " WHERE c.trip.id = :tripId AND c.parentComment IS NULL")
    Page<Comment> findTopLevelCommentsByTripId(@Param("tripId") UUID tripId, Pageable pageable);

    /** Find a comment by ID with user data eagerly loaded. */
    @Query(
            "SELECT c FROM Comment c LEFT JOIN FETCH c.user LEFT JOIN FETCH c.commentReactions cr LEFT JOIN FETCH cr.user WHERE c.id = :commentId")
    Optional<Comment> findByIdWithUser(@Param("commentId") UUID commentId);
}

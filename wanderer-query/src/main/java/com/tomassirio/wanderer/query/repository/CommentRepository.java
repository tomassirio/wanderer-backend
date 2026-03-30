package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Comment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Find all top-level comments for a trip (comments with no parent). Uses EntityGraph to
     * efficiently load user and reactions data.
     */
    @EntityGraph(attributePaths = {"user", "commentReactions", "commentReactions.user"})
    @Query(
            "SELECT c FROM Comment c WHERE c.trip.id = :tripId AND c.parentComment IS NULL ORDER BY c.timestamp DESC")
    List<Comment> findTopLevelCommentsByTripId(@Param("tripId") UUID tripId);

    /**
     * Find all top-level comments for a trip with pagination. Uses EntityGraph to efficiently load
     * user and reactions data.
     */
    @EntityGraph(attributePaths = {"user", "commentReactions", "commentReactions.user"})
    @Query(
            value = "SELECT c FROM Comment c WHERE c.trip.id = :tripId AND c.parentComment IS NULL",
            countQuery =
                    "SELECT COUNT(c) FROM Comment c WHERE c.trip.id = :tripId AND c.parentComment IS NULL")
    Page<Comment> findTopLevelCommentsByTripId(@Param("tripId") UUID tripId, Pageable pageable);

    /** Find a comment by ID with user data and reactions eagerly loaded. */
    @EntityGraph(
            attributePaths = {
                "user",
                "commentReactions",
                "commentReactions.user",
                "replies",
                "replies.user"
            })
    @Query("SELECT c FROM Comment c WHERE c.id = :commentId")
    Optional<Comment> findByIdWithUser(@Param("commentId") UUID commentId);

    /** Batch fetch comments by IDs to prevent N+1 queries */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT c FROM Comment c WHERE c.id IN :commentIds")
    List<Comment> findByIdIn(@Param("commentIds") List<UUID> commentIds);
}

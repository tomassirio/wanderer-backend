package com.tomassirio.wanderer.auth.repository;

import com.tomassirio.wanderer.auth.domain.LoginAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    /**
     * Count recent failed login attempts for an identifier within a time window.
     *
     * @param identifier username or email
     * @param since start of the time window
     * @return count of failed attempts
     */
    @Query(
            "SELECT COUNT(l) FROM LoginAttempt l WHERE l.identifier = :identifier AND l.success = false AND l.attemptedAt >= :since")
    int countRecentFailedAttempts(
            @Param("identifier") String identifier, @Param("since") Instant since);

    /**
     * Find recent login attempts for a user.
     *
     * @param userId the user ID
     * @param since start of the time window
     * @return list of login attempts
     */
    List<LoginAttempt> findByUserIdAndAttemptedAtAfterOrderByAttemptedAtDesc(
            UUID userId, Instant since);

    /**
     * Delete old login attempts to prevent table growth.
     *
     * @param before delete attempts before this timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM LoginAttempt l WHERE l.attemptedAt < :before")
    int deleteOldAttempts(@Param("before") Instant before);
}

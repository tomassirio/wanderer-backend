package com.tomassirio.wanderer.auth.repository;

import com.tomassirio.wanderer.auth.domain.RevokedAccessToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {

    /**
     * Check if a JTI is revoked.
     *
     * @param jti the JWT ID
     * @return true if the token is revoked
     */
    boolean existsByJti(String jti);

    /**
     * Delete all revoked tokens that have expired.
     * Called by scheduled cleanup job to prevent table growth.
     *
     * @param now the current timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM RevokedAccessToken r WHERE r.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}

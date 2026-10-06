package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Release;
import com.tomassirio.wanderer.commons.domain.Release.Platform;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Release queries. "Visible on a platform" = published, targets that platform and its release date
 * has passed.
 */
@Repository
public interface ReleaseRepository extends JpaRepository<Release, UUID> {

    String VISIBLE =
            " from Release r join r.platforms p where r.status = PUBLISHED"
                    + " and p.platform = :platform and p.releaseDate <= :now";

    Optional<Release> findByVersion(String version);

    @Query("select r" + VISIBLE + " and r.version = :version")
    Optional<Release> findVisible(
            @Param("version") String version,
            @Param("platform") Platform platform,
            @Param("now") Instant now);

    @Query(
            value = "select r" + VISIBLE + " order by p.releaseDate desc",
            countQuery = "select count(r)" + VISIBLE)
    Page<Release> findVisible(
            @Param("platform") Platform platform, @Param("now") Instant now, Pageable pageable);

    @Query("select r" + VISIBLE + " and r.showPopup = true")
    List<Release> findVisiblePopups(
            @Param("platform") Platform platform, @Param("now") Instant now);
}

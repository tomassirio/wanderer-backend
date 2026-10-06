package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.Release;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseRepository extends JpaRepository<Release, UUID> {

    Optional<Release> findByVersion(String version);
}

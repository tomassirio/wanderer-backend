package com.tomassirio.wanderer.command.repository;

import com.tomassirio.wanderer.commons.domain.UserReleaseSeen;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReleaseSeenRepository extends JpaRepository<UserReleaseSeen, UUID> {}

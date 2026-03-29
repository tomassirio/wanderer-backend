package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.query.dto.UserSummaryDto;
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
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    
    /**
     * Find user summary by ID (DTO projection for lightweight access)
     */
    @Query("SELECT new com.tomassirio.wanderer.query.dto.UserSummaryDto(u.id, u.username, u.userDetails.displayName, CONCAT('/thumbnails/profiles/', CAST(u.id AS string), '.png')) FROM User u WHERE u.id = :id")
    Optional<UserSummaryDto> findUserSummaryById(@Param("id") UUID id);
    
    /**
     * Find multiple user summaries by IDs (batch fetch)
     */
    @Query("SELECT new com.tomassirio.wanderer.query.dto.UserSummaryDto(u.id, u.username, u.userDetails.displayName, CONCAT('/thumbnails/profiles/', CAST(u.id AS string), '.png')) FROM User u WHERE u.id IN :ids")
    List<UserSummaryDto> findUserSummariesByIdIn(@Param("ids") List<UUID> ids);
    
    /**
     * Find user summaries with pagination
     */
    @Query("SELECT new com.tomassirio.wanderer.query.dto.UserSummaryDto(u.id, u.username, u.userDetails.displayName, CONCAT('/thumbnails/profiles/', CAST(u.id AS string), '.png')) FROM User u")
    Page<UserSummaryDto> findAllUserSummaries(Pageable pageable);
    
    /**
     * Search users by username or display name (DTO projection)
     */
    @Query("SELECT new com.tomassirio.wanderer.query.dto.UserSummaryDto(u.id, u.username, u.userDetails.displayName, CONCAT('/thumbnails/profiles/', CAST(u.id AS string), '.png')) " +
           "FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.userDetails.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<UserSummaryDto> searchUserSummaries(@Param("searchTerm") String searchTerm, Pageable pageable);
}

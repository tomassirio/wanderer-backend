package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.UserFollow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, UUID> {
    List<UserFollow> findByFollowerId(UUID followerId);

    @Query("SELECT uf FROM UserFollow uf WHERE uf.followerId = :followerId")
    Page<UserFollow> findByFollowerIdPageable(
            @Param("followerId") UUID followerId, Pageable pageable);

    List<UserFollow> findByFollowedId(UUID followedId);

    @Query("SELECT uf FROM UserFollow uf WHERE uf.followedId = :followedId")
    Page<UserFollow> findByFollowedIdPageable(
            @Param("followedId") UUID followedId, Pageable pageable);

    long countByFollowedId(UUID followedId);

    @Query(
            "SELECT DISTINCT uf.followedId FROM UserFollow uf WHERE uf.followerId IN :friendIds AND uf.followedId != :currentUserId")
    List<UUID> findUsersFollowedByFriends(
            @Param("friendIds") List<UUID> friendIds, @Param("currentUserId") UUID currentUserId);

    @Query(
            "SELECT DISTINCT uf.followedId FROM UserFollow uf WHERE uf.followerId IN :friendIds AND uf.followedId != :currentUserId")
    Page<UUID> findUsersFollowedByFriendsPageable(
            @Param("friendIds") List<UUID> friendIds,
            @Param("currentUserId") UUID currentUserId,
            Pageable pageable);
}

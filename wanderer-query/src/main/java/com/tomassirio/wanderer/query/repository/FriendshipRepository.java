package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Friendship;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    boolean existsByUserIdAndFriendId(UUID userId, UUID friendId);

    List<Friendship> findByUserId(UUID userId);

    @Query("SELECT f FROM Friendship f WHERE f.userId = :userId")
    Page<Friendship> findByUserIdPageable(@Param("userId") UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    @Query(
            "SELECT DISTINCT f.friendId FROM Friendship f WHERE f.userId IN :friendIds AND f.friendId != :currentUserId")
    List<UUID> findFriendsOfFriends(
            @Param("friendIds") List<UUID> friendIds, @Param("currentUserId") UUID currentUserId);

    @Query(
            "SELECT DISTINCT f.friendId FROM Friendship f WHERE f.userId IN :friendIds AND f.friendId != :currentUserId")
    Page<UUID> findFriendsOfFriendsPageable(
            @Param("friendIds") List<UUID> friendIds,
            @Param("currentUserId") UUID currentUserId,
            Pageable pageable);
}

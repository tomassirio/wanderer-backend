package com.tomassirio.wanderer.query.repository;

import com.tomassirio.wanderer.commons.domain.Friendship;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    boolean existsByUserIdAndFriendId(UUID userId, UUID friendId);

    List<Friendship> findByUserId(UUID userId);

    long countByUserId(UUID userId);
    
    @Query("SELECT DISTINCT f.friendId FROM Friendship f WHERE f.userId IN :friendIds AND f.friendId != :currentUserId")
    List<UUID> findFriendsOfFriends(@Param("friendIds") List<UUID> friendIds, @Param("currentUserId") UUID currentUserId);
}

package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.UserDetailsDTO;
import com.tomassirio.wanderer.commons.mapper.UserDetailsMapper;
import com.tomassirio.wanderer.query.dto.UserAdminResponse;
import com.tomassirio.wanderer.query.dto.UserRelationshipResponse;
import com.tomassirio.wanderer.query.dto.UserResponse;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.UserQueryService;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service implementation for user query operations. Handles user retrieval logic using the user
 * repository.
 *
 * @since 0.1.8
 */
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserFollowRepository userFollowRepository;
    private final TripRepository tripRepository;

    @Override
    public UserResponse getUserById(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing or invalid authenticated user id");
        }
        var user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return toUserResponse(user);
    }

    @Override
    public UserResponse getUserByUsername(String username) {
        var user =
                userRepository
                        .findByUsername(username.toLowerCase(Locale.ROOT))
                        .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return toUserResponse(user);
    }

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toUserResponse);
    }

    @Override
    public Page<UserAdminResponse> getAllUsersWithStats(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToAdminResponse);
    }

    @Override
    public List<UserResponse> getDiscoverableUsers(UUID currentUserId) {
        List<UUID> myFriendIds = friendshipRepository.findByUserId(currentUserId).stream()
                .map(Friendship::getFriendId)
                .collect(Collectors.toList());
        
        if (myFriendIds.isEmpty()) {
            return List.of();
        }

        List<UUID> friendsOfFriends = friendshipRepository.findFriendsOfFriends(myFriendIds, currentUserId);
        Set<UUID> discoverableUserIds = new LinkedHashSet<>(friendsOfFriends);
        
        List<UUID> usersFollowedByFriends = userFollowRepository.findUsersFollowedByFriends(myFriendIds, currentUserId);
        discoverableUserIds.addAll(usersFollowedByFriends);
        
        myFriendIds.forEach(discoverableUserIds::remove);
        
        if (discoverableUserIds.isEmpty()) {
            return List.of();
        }
        
        return userRepository.findAllById(new ArrayList<>(discoverableUserIds)).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<UserRelationshipResponse> getAssociatedUsers(UUID currentUserId, UUID targetUserId) {
        List<UUID> targetUserFriendIds = friendshipRepository.findByUserId(targetUserId).stream()
                .map(Friendship::getFriendId)
                .toList();
        
        List<UUID> targetUserFollowingIds = userFollowRepository.findByFollowerId(targetUserId).stream()
                .map(UserFollow::getFollowedId)
                .toList();
        
        List<UUID> targetUserFollowerIds = userFollowRepository.findByFollowedId(targetUserId).stream()
                .map(UserFollow::getFollowerId)
                .toList();
        
        Set<UUID> allAssociatedUserIds = new LinkedHashSet<>();
        allAssociatedUserIds.addAll(targetUserFriendIds);
        allAssociatedUserIds.addAll(targetUserFollowingIds);
        allAssociatedUserIds.addAll(targetUserFollowerIds);
        allAssociatedUserIds.remove(targetUserId);
        
        if (allAssociatedUserIds.isEmpty()) {
            return List.of();
        }
        
        List<UUID> currentUserFriendIds = friendshipRepository.findByUserId(currentUserId).stream()
                .map(Friendship::getFriendId)
                .collect(Collectors.toList());
        
        List<UUID> currentUserFollowingIds = userFollowRepository.findByFollowerId(currentUserId).stream()
                .map(UserFollow::getFollowedId)
                .collect(Collectors.toList());
        
        List<UUID> currentUserFollowerIds = userFollowRepository.findByFollowedId(currentUserId).stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toList());
        
        return userRepository.findAllById(new ArrayList<>(allAssociatedUserIds)).stream()
                .map(user -> toUserRelationshipResponse(
                        user, 
                        currentUserFriendIds, 
                        currentUserFollowingIds, 
                        currentUserFollowerIds))
                .collect(Collectors.toList());
    }

    private UserResponse toUserResponse(User user) {
        UserDetailsDTO detailsDTO = UserDetailsMapper.INSTANCE.toDTO(user.getUserDetails());
        return new UserResponse(user.getId(), user.getUsername(), detailsDTO);
    }

    private UserAdminResponse mapToAdminResponse(User user) {
        long friendsCount = friendshipRepository.countByUserId(user.getId());
        long followersCount = userFollowRepository.countByFollowedId(user.getId());
        long tripsCount = tripRepository.countByUserId(user.getId());
        UserDetailsDTO detailsDTO = UserDetailsMapper.INSTANCE.toDTO(user.getUserDetails());

        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                detailsDTO,
                friendsCount,
                followersCount,
                tripsCount,
                user.getCreatedAt());
    }
    
    private UserRelationshipResponse toUserRelationshipResponse(
            User user,
            List<UUID> currentUserFriendIds,
            List<UUID> currentUserFollowingIds,
            List<UUID> currentUserFollowerIds) {
        UserDetailsDTO detailsDTO = UserDetailsMapper.INSTANCE.toDTO(user.getUserDetails());
        
        return new UserRelationshipResponse(
                user.getId(),
                user.getUsername(),
                detailsDTO,
                currentUserFriendIds.contains(user.getId()),
                currentUserFollowingIds.contains(user.getId()),
                currentUserFollowerIds.contains(user.getId()));
    }
}

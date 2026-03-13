package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserDetailsDTO;
import com.tomassirio.wanderer.commons.mapper.UserDetailsMapper;
import com.tomassirio.wanderer.query.dto.UserAdminResponse;
import com.tomassirio.wanderer.query.dto.UserResponse;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.repository.UserRepository;
import com.tomassirio.wanderer.query.service.UserQueryService;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
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
                        .findByUsername(username.toLowerCase(java.util.Locale.ROOT))
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
}

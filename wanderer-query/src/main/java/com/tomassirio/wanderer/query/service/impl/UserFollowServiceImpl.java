package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.UserFollow;
import com.tomassirio.wanderer.commons.dto.UserFollowResponse;
import com.tomassirio.wanderer.query.repository.UserFollowRepository;
import com.tomassirio.wanderer.query.service.UserFollowService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service implementation for querying user follows.
 *
 * @author tomassirio
 * @since 0.4.5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserFollowServiceImpl implements UserFollowService {

    private final UserFollowRepository userFollowRepository;

    @Override
    public List<UserFollowResponse> getFollowing(UUID followerId) {
        log.info("Retrieving following list for user: {}", followerId);
        List<UserFollow> follows = userFollowRepository.findByFollowerId(followerId);
        log.info("Found {} users that user {} is following", follows.size(), followerId);
        return follows.stream().map(this::toResponse).toList();
    }

    @Override
    public Page<UserFollowResponse> getFollowing(UUID followerId, Pageable pageable) {
        log.info("Retrieving following list for user: {} with pagination", followerId);
        Page<UserFollow> follows = userFollowRepository.findByFollowerIdPageable(followerId, pageable);
        log.info("Found {} users that user {} is following (page {} of {})", 
                follows.getNumberOfElements(), followerId, follows.getNumber() + 1, follows.getTotalPages());
        return follows.map(this::toResponse);
    }

    @Override
    public List<UserFollowResponse> getFollowers(UUID followedId) {
        log.info("Retrieving followers list for user: {}", followedId);
        List<UserFollow> followers = userFollowRepository.findByFollowedId(followedId);
        log.info("Found {} followers for user {}", followers.size(), followedId);
        return followers.stream().map(this::toResponse).toList();
    }

    @Override
    public Page<UserFollowResponse> getFollowers(UUID followedId, Pageable pageable) {
        log.info("Retrieving followers list for user: {} with pagination", followedId);
        Page<UserFollow> followers = userFollowRepository.findByFollowedIdPageable(followedId, pageable);
        log.info("Found {} followers for user {} (page {} of {})", 
                followers.getNumberOfElements(), followedId, followers.getNumber() + 1, followers.getTotalPages());
        return followers.map(this::toResponse);
    }

    private UserFollowResponse toResponse(UserFollow userFollow) {
        return new UserFollowResponse(
                userFollow.getId(),
                userFollow.getFollowerId(),
                userFollow.getFollowedId(),
                userFollow.getCreatedAt());
    }
}

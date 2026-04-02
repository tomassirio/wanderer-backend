package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.dto.FriendshipResponse;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.service.FriendshipQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipQueryServiceImpl implements FriendshipQueryService {

    private final FriendshipRepository friendshipRepository;

    @Override
    public List<FriendshipResponse> getFriends(UUID userId) {
        log.info("Getting friends for user {}", userId);
        return friendshipRepository.findByUserId(userId).stream()
                .map(
                        friendship ->
                                new FriendshipResponse(
                                        friendship.getUserId(), friendship.getFriendId()))
                .toList();
    }

    @Override
    public Page<FriendshipResponse> getFriends(UUID userId, Pageable pageable) {
        log.info("Getting friends for user {} with pagination", userId);
        Page<FriendshipResponse> friends =
                friendshipRepository
                        .findByUserIdPageable(userId, pageable)
                        .map(
                                friendship ->
                                        new FriendshipResponse(
                                                friendship.getUserId(), friendship.getFriendId()));
        log.info(
                "Found {} friends for user {} (page {} of {})",
                friends.getNumberOfElements(),
                userId,
                friends.getNumber() + 1,
                friends.getTotalPages());
        return friends;
    }
}

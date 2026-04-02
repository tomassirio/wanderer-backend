package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.dto.AchievementDTO;
import com.tomassirio.wanderer.commons.dto.UserAchievementDTO;
import com.tomassirio.wanderer.commons.mapper.AchievementMapper;
import com.tomassirio.wanderer.commons.mapper.UserAchievementMapper;
import com.tomassirio.wanderer.query.repository.AchievementRepository;
import com.tomassirio.wanderer.query.repository.UserAchievementRepository;
import com.tomassirio.wanderer.query.service.AchievementQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service implementation for achievement query operations. Handles achievement retrieval logic
 * using the achievement repositories.
 */
@Service
@RequiredArgsConstructor
public class AchievementQueryServiceImpl implements AchievementQueryService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;

    @Override
    // TODO: Re-enable caching after fixing serialization issue with nested DTOs
    // @Cacheable(value = RedisCacheConfig.ACHIEVEMENTS_CACHE, key = "'available'")
    public List<AchievementDTO> getAvailableAchievements() {
        return achievementRepository.findByEnabledTrue().stream()
                .map(AchievementMapper.INSTANCE::toDTO)
                .toList();
    }

    @Override
    // TODO: Re-enable caching after fixing serialization issue with nested DTOs
    // @Cacheable(value = RedisCacheConfig.USER_ACHIEVEMENTS_CACHE, key = "#userId")
    public List<UserAchievementDTO> getUserAchievements(UUID userId) {
        return userAchievementRepository.findByUserId(userId).stream()
                .map(UserAchievementMapper.INSTANCE::toDTO)
                .toList();
    }

    @Override
    // TODO: Re-enable caching after fixing serialization issue with nested DTOs
    // @Cacheable(value = RedisCacheConfig.USER_ACHIEVEMENTS_CACHE, key = "#userId + '-' + #tripId")
    public List<UserAchievementDTO> getUserAchievementsByTrip(UUID userId, UUID tripId) {
        return userAchievementRepository.findByUserIdAndTripId(userId, tripId).stream()
                .map(UserAchievementMapper.INSTANCE::toDTO)
                .toList();
    }

    @Override
    public List<UserAchievementDTO> getTripAchievements(UUID tripId) {
        return userAchievementRepository.findByTripId(tripId).stream()
                .map(UserAchievementMapper.INSTANCE::toDTO)
                .toList();
    }
}

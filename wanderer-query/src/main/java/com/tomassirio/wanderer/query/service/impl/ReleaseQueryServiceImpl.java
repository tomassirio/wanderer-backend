package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.domain.Release;
import com.tomassirio.wanderer.commons.domain.Release.Platform;
import com.tomassirio.wanderer.commons.domain.UserReleaseSeen;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import com.tomassirio.wanderer.commons.dto.UnreadReleasesDTO;
import com.tomassirio.wanderer.query.repository.ReleaseRepository;
import com.tomassirio.wanderer.query.repository.UserReleaseSeenRepository;
import com.tomassirio.wanderer.query.service.ReleaseQueryService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseQueryServiceImpl implements ReleaseQueryService {

    private final ReleaseRepository releaseRepository;
    private final UserReleaseSeenRepository userReleaseSeenRepository;

    @Override
    public ReleaseDTO getVisible(String version, String platform) {
        Release.requireVersion(version);
        return releaseRepository
                .findVisible(version, Platform.parse(platform), Instant.now())
                .map(ReleaseDTO::from)
                .orElseThrow(() -> new EntityNotFoundException("Release not visible: " + version));
    }

    @Override
    public Page<ReleaseDTO> getHistory(String platform, Pageable pageable) {
        return releaseRepository
                .findVisible(Platform.parse(platform), Instant.now(), pageable)
                .map(ReleaseDTO::from);
    }

    /**
     * Visible popup releases with {@code lastSeen < version <= currentVersion}. A user without a
     * last-seen version is treated as having seen everything up to {@code currentVersion}.
     */
    @Override
    public UnreadReleasesDTO getUnread(UUID userId, String platform, String currentVersion) {
        Platform p = Platform.parse(platform);
        Release.requireVersion(currentVersion);
        String lastSeen =
                userReleaseSeenRepository
                        .findById(userId)
                        .map(UserReleaseSeen::getLastSeenVersion)
                        .orElse(null);
        if (lastSeen == null) {
            return new UnreadReleasesDTO(null, List.of());
        }
        List<ReleaseDTO> unread =
                releaseRepository.findVisiblePopups(p, Instant.now()).stream()
                        .filter(r -> Release.compareVersions(r.getVersion(), lastSeen) > 0)
                        .filter(r -> Release.compareVersions(r.getVersion(), currentVersion) <= 0)
                        .sorted((a, b) -> Release.compareVersions(b.getVersion(), a.getVersion()))
                        .map(ReleaseDTO::from)
                        .toList();
        return new UnreadReleasesDTO(lastSeen, unread);
    }

    @Override
    public Page<ReleaseDTO> getAll(Pageable pageable) {
        return releaseRepository.findAll(pageable).map(ReleaseDTO::from);
    }

    @Override
    public ReleaseDTO getByVersion(String version) {
        return releaseRepository
                .findByVersion(version)
                .map(ReleaseDTO::from)
                .orElseThrow(() -> new EntityNotFoundException("Release not found: " + version));
    }
}

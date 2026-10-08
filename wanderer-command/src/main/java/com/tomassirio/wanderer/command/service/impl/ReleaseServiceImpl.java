package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.controller.request.ReleaseDraftRequest;
import com.tomassirio.wanderer.command.controller.request.ReleaseUpdateRequest;
import com.tomassirio.wanderer.command.repository.ReleaseRepository;
import com.tomassirio.wanderer.command.repository.UserReleaseSeenRepository;
import com.tomassirio.wanderer.command.service.ReleaseService;
import com.tomassirio.wanderer.commons.domain.Release;
import com.tomassirio.wanderer.commons.domain.Release.Item;
import com.tomassirio.wanderer.commons.domain.Release.ItemType;
import com.tomassirio.wanderer.commons.domain.Release.Platform;
import com.tomassirio.wanderer.commons.domain.Release.PlatformRelease;
import com.tomassirio.wanderer.commons.domain.UserReleaseSeen;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReleaseServiceImpl implements ReleaseService {

    private static final List<Platform> DEFAULT_PLATFORMS = List.of(Platform.ANDROID, Platform.WEB);

    private final ReleaseRepository releaseRepository;
    private final UserReleaseSeenRepository userReleaseSeenRepository;

    @Override
    public ReleaseDTO update(String version, ReleaseUpdateRequest request) {
        // Unknown version: the admin is writing notes CI hasn't drafted (yet), so start a draft.
        Release release =
                releaseRepository.findByVersion(version).orElseGet(() -> newDraft(version));
        if (request.platforms().stream().map(PlatformRelease::platform).distinct().count()
                != request.platforms().size()) {
            throw new IllegalArgumentException("Each platform may appear only once");
        }
        release.setHeadline(request.headline());
        release.setShowPopup(request.showPopup());
        release.getPlatforms().clear();
        release.getPlatforms().addAll(request.platforms());
        release.getItems().clear();
        release.getItems().addAll(request.items());
        log.info("Release {} updated", version);
        return ReleaseDTO.from(releaseRepository.saveAndFlush(release));
    }

    @Override
    public ReleaseDTO publish(String version) {
        Release release = get(version);
        if (release.getHeadline() == null || release.getHeadline().isBlank()) {
            throw new IllegalArgumentException("A headline is required to publish");
        }
        if (release.getPlatforms().isEmpty()) {
            throw new IllegalArgumentException("At least one platform is required to publish");
        }
        // Platforms without a date go live now; dates already set (e.g. a later rollout) are kept.
        Instant now = Instant.now();
        release.getPlatforms()
                .replaceAll(
                        p -> p.releaseDate() == null ? new PlatformRelease(p.platform(), now) : p);
        release.setStatus(Release.Status.PUBLISHED);
        log.info("Release {} published", version);
        return ReleaseDTO.from(releaseRepository.saveAndFlush(release));
    }

    @Override
    public DraftResult upsertDraft(ReleaseDraftRequest request) {
        String version = Release.requireVersion(request.version());
        // ponytail: concurrent first calls for one version race on uk_releases_version; the loser
        // gets a 500 and CI's retry lands as an update.
        Release release = releaseRepository.findByVersion(version).orElse(null);
        boolean created = release == null;
        if (created) {
            release = newDraft(version);
            List<Platform> platforms =
                    request.platforms() == null || request.platforms().isEmpty()
                            ? DEFAULT_PLATFORMS
                            : request.platforms().stream().distinct().toList();
            for (Platform p : platforms) {
                release.getPlatforms().add(new PlatformRelease(p, null));
            }
        } else if (release.getStatus() == Release.Status.PUBLISHED) {
            throw new IllegalStateException("Release " + version + " is already published");
        }

        List<Item> items = release.getItems();
        for (ReleaseDraftRequest.PullRequest pr : request.prs()) {
            Item item =
                    new Item(
                            typeOf(pr.label()),
                            truncate(pr.title(), Release.MAX_TITLE),
                            truncate(pr.summary(), Release.MAX_TEXT),
                            pr.number());
            int existing = indexOfPr(items, pr.number());
            if (existing >= 0) {
                items.set(existing, item);
            } else {
                items.add(item);
            }
        }
        log.info(
                "Release draft {} {} with {} PRs",
                version,
                created ? "created" : "updated",
                request.prs().size());
        return new DraftResult(ReleaseDTO.from(releaseRepository.saveAndFlush(release)), created);
    }

    @Override
    public String markSeen(UUID userId, String version) {
        Release.requireVersion(version);
        UserReleaseSeen seen =
                userReleaseSeenRepository
                        .findById(userId)
                        .orElseGet(() -> new UserReleaseSeen(userId, version, null));
        if (Release.compareVersions(version, seen.getLastSeenVersion()) > 0) {
            seen.setLastSeenVersion(version);
        }
        return userReleaseSeenRepository.save(seen).getLastSeenVersion();
    }

    private static Release newDraft(String version) {
        Release release = new Release();
        release.setId(UUID.randomUUID());
        release.setVersion(Release.requireVersion(version));
        release.setStatus(Release.Status.DRAFT);
        return release;
    }

    private Release get(String version) {
        return releaseRepository
                .findByVersion(version)
                .orElseThrow(() -> new EntityNotFoundException("Release not found: " + version));
    }

    static ItemType typeOf(String label) {
        String l = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        return switch (l) {
            case "new", "feature", "feat" -> ItemType.NEW;
            case "fixed", "fix", "bug", "bugfix" -> ItemType.FIXED;
            default -> ItemType.IMPROVED;
        };
    }

    private static int indexOfPr(List<Item> items, Integer prNumber) {
        for (int i = 0; i < items.size(); i++) {
            if (prNumber.equals(items.get(i).prNumber())) return i;
        }
        return -1;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

package com.tomassirio.wanderer.query.service;

import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import com.tomassirio.wanderer.commons.dto.UnreadReleasesDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Read side of release notes ("What's new"). */
public interface ReleaseQueryService {

    ReleaseDTO getVisible(String version, String platform);

    Page<ReleaseDTO> getHistory(String platform, Pageable pageable);

    UnreadReleasesDTO getUnread(UUID userId, String platform, String currentVersion);

    Page<ReleaseDTO> getAll(Pageable pageable);

    ReleaseDTO getByVersion(String version);
}

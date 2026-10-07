package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.command.controller.request.ReleaseDraftRequest;
import com.tomassirio.wanderer.command.controller.request.ReleaseUpdateRequest;
import com.tomassirio.wanderer.commons.dto.ReleaseDTO;
import java.util.UUID;

/** Write side of release notes ("What's new"). */
public interface ReleaseService {

    ReleaseDTO update(String version, ReleaseUpdateRequest request);

    ReleaseDTO publish(String version);

    DraftResult upsertDraft(ReleaseDraftRequest request);

    /** Moves the user's last-seen version forward (never back). Returns the stored version. */
    String markSeen(UUID userId, String version);

    record DraftResult(ReleaseDTO release, boolean created) {}
}

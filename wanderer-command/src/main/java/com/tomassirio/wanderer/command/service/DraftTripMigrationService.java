package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.commons.dto.DraftMigrationReportDTO;

/** Converts Draft (CREATED) trips into trip plans now that new trips start live. */
public interface DraftTripMigrationService {

    /**
     * Converts Drafts that have a route and no check-ins into trip plans (keeping name, type,
     * dates, and visibility in the plan metadata) and removes them; removes Drafts whose source
     * plan still exists. Drafts with check-ins or without a route are only reported.
     *
     * @param dryRun when {@code true}, only reports what would happen
     * @return counts and the Drafts that need manual review
     */
    DraftMigrationReportDTO migrateDrafts(boolean dryRun);
}

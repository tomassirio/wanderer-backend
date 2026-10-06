package com.tomassirio.wanderer.commons.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of migrating Draft (CREATED) trips to trip plans.
 *
 * @param dryRun {@code true} when nothing was written
 * @param totalDrafts Draft trips found
 * @param toConvert Drafts with a route and no check-ins that become a new plan
 * @param alreadyPlanned Drafts created from a still-existing plan; only the Draft is removed
 * @param converted Drafts actually converted or removed (0 on a dry run)
 * @param withCheckIns Drafts with check-ins: never touched, listed for manual review
 * @param missingRoute Drafts without start/end location (plans require both): left untouched
 */
public record DraftMigrationReportDTO(
        boolean dryRun,
        int totalDrafts,
        int toConvert,
        int alreadyPlanned,
        int converted,
        List<DraftTrip> withCheckIns,
        List<DraftTrip> missingRoute) {

    public record DraftTrip(UUID tripId, UUID userId, String name, long checkIns) {}
}

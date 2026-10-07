package com.tomassirio.wanderer.command.service.impl;

import com.tomassirio.wanderer.command.event.TripDeletedEvent;
import com.tomassirio.wanderer.command.event.TripPlanCreatedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.TripUpdateRepository;
import com.tomassirio.wanderer.command.service.DraftTripMigrationService;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripDetails;
import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.dto.DraftMigrationReportDTO;
import com.tomassirio.wanderer.commons.dto.DraftMigrationReportDTO.DraftTrip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DraftTripMigrationServiceImpl implements DraftTripMigrationService {

    private final TripRepository tripRepository;
    private final TripUpdateRepository tripUpdateRepository;
    private final TripPlanRepository tripPlanRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public DraftMigrationReportDTO migrateDrafts(boolean dryRun) {
        List<Trip> drafts = tripRepository.findByTripSettingsTripStatus(TripStatus.CREATED);
        List<DraftTrip> withCheckIns = new ArrayList<>();
        List<DraftTrip> missingRoute = new ArrayList<>();
        int toConvert = 0;
        int alreadyPlanned = 0;

        for (Trip trip : drafts) {
            long checkIns = tripUpdateRepository.countByTripId(trip.getId());
            DraftTrip entry =
                    new DraftTrip(trip.getId(), trip.getUserId(), trip.getName(), checkIns);
            if (checkIns > 0) {
                withCheckIns.add(entry);
            } else if (trip.getTripPlanId() != null
                    && tripPlanRepository.existsById(trip.getTripPlanId())) {
                alreadyPlanned++;
                if (!dryRun) {
                    delete(trip);
                }
            } else {
                if (!hasRoute(trip.getTripDetails())) {
                    missingRoute.add(entry); // informational: converted without a route
                }
                toConvert++;
                if (!dryRun) {
                    eventPublisher.publishEvent(toPlanEvent(trip));
                    delete(trip);
                }
            }
        }

        log.info(
                "Draft migration (dryRun={}): {} drafts, {} to convert, {} already planned,"
                        + " {} with check-ins, {} of those converted have no route",
                dryRun,
                drafts.size(),
                toConvert,
                alreadyPlanned,
                withCheckIns.size(),
                missingRoute.size());
        return new DraftMigrationReportDTO(
                dryRun,
                drafts.size(),
                toConvert,
                alreadyPlanned,
                dryRun ? 0 : toConvert + alreadyPlanned,
                withCheckIns,
                missingRoute);
    }

    private static boolean hasRoute(TripDetails details) {
        return details != null
                && details.getStartLocation() != null
                && details.getEndLocation() != null;
    }

    private void delete(Trip trip) {
        eventPublisher.publishEvent(
                TripDeletedEvent.builder().tripId(trip.getId()).ownerId(trip.getUserId()).build());
    }

    private static TripPlanCreatedEvent toPlanEvent(Trip trip) {
        TripDetails details =
                Optional.ofNullable(trip.getTripDetails()).orElseGet(TripDetails::new);
        LocalDate start =
                toDate(
                        Optional.ofNullable(details.getStartTimestamp())
                                .orElse(trip.getCreationTimestamp()));
        LocalDate end =
                Optional.ofNullable(details.getEndTimestamp())
                        .map(DraftTripMigrationServiceImpl::toDate)
                        .filter(d -> !d.isBefore(start))
                        .orElse(start);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("visibility", trip.getTripSettings().getVisibility().name());
        metadata.put("migratedFromTripId", trip.getId().toString());
        return TripPlanCreatedEvent.builder()
                .tripPlanId(UUID.randomUUID())
                .userId(trip.getUserId())
                .name(trip.getName())
                .planType(
                        trip.getTripSettings().getTripModality() == TripModality.MULTI_DAY
                                ? TripPlanType.MULTI_DAY
                                : TripPlanType.SIMPLE)
                .startDate(start)
                .endDate(end)
                .startLocation(details.getStartLocation())
                .endLocation(details.getEndLocation())
                .waypoints(details.getWaypoints())
                .metadata(metadata)
                .createdTimestamp(Instant.now())
                .plannedPolyline(trip.getPlannedPolyline())
                .build();
    }

    private static LocalDate toDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}

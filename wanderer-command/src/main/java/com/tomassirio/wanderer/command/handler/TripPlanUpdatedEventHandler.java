package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripPlanUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.TripPlanMetadataProcessor;
import com.tomassirio.wanderer.command.service.TripPlanPolylineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for persisting trip plan update events to the database.
 *
 * <p>This handler implements the CQRS write side by handling TripPlanUpdatedEvent and updating trip
 * plans in the database. Validation is performed in the service layer before the event is emitted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripPlanUpdatedEventHandler implements EventHandler<TripPlanUpdatedEvent> {

    private final TripPlanRepository tripPlanRepository;
    private final TripPlanMetadataProcessor metadataProcessor;
    private final TripPlanPolylineService tripPlanPolylineService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripPlanUpdatedEvent event) {
        log.debug("Persisting TripPlanUpdatedEvent for trip plan: {}", event.getTripPlanId());

        tripPlanRepository
                .findById(event.getTripPlanId())
                .ifPresent(
                        tripPlan -> {
                            tripPlan.setName(event.getName());
                            tripPlan.setStartDate(event.getStartDate());
                            tripPlan.setEndDate(event.getEndDate());
                            tripPlan.setStartLocation(event.getStartLocation());
                            tripPlan.setEndLocation(event.getEndLocation());
                            tripPlan.setWaypoints(
                                    event.getWaypoints() != null
                                            ? event.getWaypoints()
                                            : List.of());
                            tripPlan.setPlannedPolyline(event.getPlannedPolyline());

                            // Re-validate metadata for the plan type
                            metadataProcessor.applyMetadata(tripPlan, tripPlan.getMetadata());

                            // No need to call save() - entity is managed and will be flushed
                            // automatically
                            log.info("Trip plan updated and persisted: {}", event.getTripPlanId());
                        });

        // Recompute polyline since locations/waypoints may have changed
        tripPlanPolylineService.computePolyline(event.getTripPlanId());
    }
}

package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripPlanCreatedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.TripPlanPolylineService;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripPlanCreatedEventHandler implements EventHandler<TripPlanCreatedEvent> {

    private final TripPlanRepository tripPlanRepository;
    private final TripPlanPolylineService tripPlanPolylineService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripPlanCreatedEvent event) {
        log.debug("Persisting TripPlanCreatedEvent for trip plan: {}", event.getTripPlanId());

        TripPlan tripPlan =
                TripPlan.builder()
                        .id(event.getTripPlanId())
                        .name(event.getName())
                        .planType(event.getPlanType())
                        .userId(event.getUserId())
                        .createdTimestamp(event.getCreatedTimestamp())
                        .startDate(event.getStartDate())
                        .endDate(event.getEndDate())
                        .startLocation(event.getStartLocation())
                        .endLocation(event.getEndLocation())
                        .waypoints(
                                Optional.ofNullable(event.getWaypoints()).orElseGet(ArrayList::new))
                        .metadata(Optional.ofNullable(event.getMetadata()).orElseGet(HashMap::new))
                        .plannedPolyline(event.getPlannedPolyline())
                        .build();

        tripPlanRepository.save(tripPlan);
        log.info("Trip plan created and persisted: {}", event.getTripPlanId());

        // Compute polyline from planned route (start → waypoints → end)
        tripPlanPolylineService.computePolyline(event.getTripPlanId());
    }
}

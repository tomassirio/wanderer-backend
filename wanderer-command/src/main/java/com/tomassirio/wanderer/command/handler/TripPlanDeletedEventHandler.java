package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripPlanDeletedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripPlanDeletedEventHandler implements EventHandler<TripPlanDeletedEvent> {

    private final TripPlanRepository tripPlanRepository;
    private final ThumbnailService thumbnailService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripPlanDeletedEvent event) {
        log.debug("Persisting TripPlanDeletedEvent for trip plan: {}", event.getTripPlanId());

        tripPlanRepository.deleteById(event.getTripPlanId());
        log.info("Trip plan deleted: {}", event.getTripPlanId());

        // Delete the associated thumbnail
        thumbnailService.deleteThumbnail(event.getTripPlanId(), ThumbnailEntityType.TRIP_PLAN);
    }
}

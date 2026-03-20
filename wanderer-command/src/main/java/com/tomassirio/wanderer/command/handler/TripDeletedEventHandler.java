package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripDeletedEvent;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event handler for TripDeletedEvent that handles persistence.
 *
 * <p>This handler deletes the trip during the transaction. WebSocket broadcasting is handled
 * centrally by {@link com.tomassirio.wanderer.command.websocket.BroadcastableEventListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripDeletedEventHandler implements EventHandler<TripDeletedEvent> {

    private final TripRepository tripRepository;
    private final ThumbnailService thumbnailService;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TripDeletedEvent event) {
        log.debug("Persisting TripDeletedEvent for trip: {}", event.getTripId());

        tripRepository.deleteById(event.getTripId());
        log.info("Trip deleted: {}", event.getTripId());

        thumbnailService.deleteThumbnail(event.getTripId());
    }
}

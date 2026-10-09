package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TrackPointsRecordedEvent;
import com.tomassirio.wanderer.command.repository.TripTrackPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists uploaded track points; ids that already exist are skipped (idempotent uploads). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackPointsRecordedEventHandler implements EventHandler<TrackPointsRecordedEvent> {

    private final TripTrackPointRepository trackPointRepository;

    @Override
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(TrackPointsRecordedEvent event) {
        // ponytail: one INSERT per point (max 500 per request); a multi-row insert if this shows
        // up in latency.
        event.getPoints().forEach(trackPointRepository::insertIgnoringDuplicate);
        log.debug(
                "Persisted {} track points for trip {}",
                event.getPoints().size(),
                event.getTripId());
    }
}

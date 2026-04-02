package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.TripCreatedEvent;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event handler for copying trip plan thumbnails when a trip is created from a plan.
 *
 * <p>When a trip is created from a trip plan, this handler copies the plan's thumbnail
 * to the trip's thumbnail location. This ensures the trip has a thumbnail immediately,
 * which will be replaced when the first trip update is added.
 *
 * @author tomassirio
 * @since 1.2.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripCreatedThumbnailHandler implements EventHandler<TripCreatedEvent> {

    private static final String PNG_EXTENSION = ".png";
    private static final String THUMBNAIL_BASE_PATH = "/data/thumbnails";

    private final ThumbnailService thumbnailService;

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(TripCreatedEvent event) {
        // Only copy thumbnail if trip was created from a plan
        if (event.getTripPlanId() == null) {
            log.debug("Trip {} was not created from a plan, skipping thumbnail copy", event.getTripId());
            return;
        }

        log.debug("Copying thumbnail from trip plan {} to trip {}", 
                  event.getTripPlanId(), event.getTripId());

        try {
            // Check if trip plan has a thumbnail
            if (!thumbnailService.thumbnailExists(event.getTripPlanId(), ThumbnailEntityType.TRIP_PLAN)) {
                log.debug("Trip plan {} has no thumbnail, skipping copy", event.getTripPlanId());
                return;
            }

            // Copy the thumbnail file
            Path planThumbnail = getThumbnailPath(event.getTripPlanId(), ThumbnailEntityType.TRIP_PLAN);
            Path tripThumbnail = getThumbnailPath(event.getTripId(), ThumbnailEntityType.TRIP);

            // Ensure trip thumbnails directory exists
            Files.createDirectories(tripThumbnail.getParent());

            // Copy the file
            Files.copy(planThumbnail, tripThumbnail, StandardCopyOption.REPLACE_EXISTING);

            log.info("Successfully copied thumbnail from trip plan {} to trip {}", 
                     event.getTripPlanId(), event.getTripId());

        } catch (IOException e) {
            log.error("Failed to copy thumbnail from trip plan {} to trip {}", 
                      event.getTripPlanId(), event.getTripId(), e);
        }
    }

    private Path getThumbnailPath(Object id, ThumbnailEntityType entityType) {
        return Paths.get(THUMBNAIL_BASE_PATH, entityType.getSubdirectory(), id + PNG_EXTENSION);
    }
}

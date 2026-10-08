package com.tomassirio.wanderer.command.event;

import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Newly uploaded track points for a trip, ordered by {@code recordedAt}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackPointsRecordedEvent implements DomainEvent {
    private UUID tripId;
    private List<TripTrackPoint> points;
}

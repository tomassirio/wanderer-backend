package com.tomassirio.wanderer.command.websocket.payload;

import com.tomassirio.wanderer.commons.dto.TrackPointDTO;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackUpdatedPayload {
    private UUID tripId;
    private List<TrackPointDTO> points;
    private Double distanceKm;
}

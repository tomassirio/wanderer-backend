package com.tomassirio.wanderer.command.service.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackGeometryTest {

    // ~1.11 m of latitude per 0.00001 degree
    private static final double DEG_PER_M = 1.0 / 111_195.0;

    private static TripTrackPoint point(double northM, Double accuracy) {
        return TripTrackPoint.builder()
                .id(UUID.randomUUID())
                .lat(52.0 + northM * DEG_PER_M)
                .lon(5.0)
                .accuracyM(accuracy)
                .recordedAt(Instant.now())
                .build();
    }

    @Test
    void filterJitter_dropsInaccurateAndTooCloseFixes() {
        List<LatLng> kept =
                TrackGeometry.filterJitter(
                        List.of(
                                point(0, 5.0),
                                point(3, 3.0), // < 5 m from previous kept: jitter
                                point(500, 150.0), // accuracy > 100 m: dropped
                                point(10, 20.0), // 10 m away but accuracy 20 m: jitter
                                point(30, 8.0), // 30 m: kept
                                point(36, null))); // 6 m, no accuracy -> 5 m floor: kept

        assertThat(kept).hasSize(3);
        assertThat(kept.get(1).lat).isCloseTo(52.0 + 30 * DEG_PER_M, within(1e-9));
    }

    @Test
    void filterJitter_keepsFirstPoint() {
        assertThat(TrackGeometry.filterJitter(List.of(point(0, 50.0)))).hasSize(1);
        assertThat(TrackGeometry.filterJitter(List.of())).isEmpty();
    }

    @Test
    void distanceKm_sumsHaversineSegments() {
        List<LatLng> path =
                List.of(new LatLng(52.0, 5.0), new LatLng(52.01, 5.0), new LatLng(52.02, 5.0));

        assertThat(TrackGeometry.distanceKm(path)).isCloseTo(2.2239, within(0.001));
        assertThat(TrackGeometry.distanceKm(List.of(new LatLng(52.0, 5.0)))).isZero();
    }

    @Test
    void simplify_collapsesStraightLineToEndpoints() {
        List<LatLng> line = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            line.add(new LatLng(52.0 + i * 10 * DEG_PER_M, 5.0));
        }

        assertThat(TrackGeometry.simplify(line, 5.0))
                .containsExactly(line.getFirst(), line.getLast());
    }

    @Test
    void simplify_keepsCornerBeyondTolerance() {
        LatLng start = new LatLng(52.0, 5.0);
        LatLng corner = new LatLng(52.0 + 100 * DEG_PER_M, 5.0);
        LatLng end = new LatLng(52.0 + 100 * DEG_PER_M, 5.002);
        LatLng onLeg = new LatLng(52.0 + 50 * DEG_PER_M, 5.0);

        assertThat(TrackGeometry.simplify(List.of(start, onLeg, corner, end), 5.0))
                .containsExactly(start, corner, end);
    }

    @Test
    void simplify_dropsWobbleWithinTolerance() {
        LatLng start = new LatLng(52.0, 5.0);
        LatLng wobble = new LatLng(52.0 + 50 * DEG_PER_M, 5.00003); // ~2 m off the line
        LatLng end = new LatLng(52.0 + 100 * DEG_PER_M, 5.0);

        assertThat(TrackGeometry.simplify(List.of(start, wobble, end), 5.0))
                .containsExactly(start, end);
    }
}

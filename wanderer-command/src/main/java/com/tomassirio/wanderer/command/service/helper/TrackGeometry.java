package com.tomassirio.wanderer.command.service.helper;

import com.google.maps.model.LatLng;
import com.tomassirio.wanderer.command.service.impl.strategy.HaversineDistanceStrategy;
import com.tomassirio.wanderer.commons.domain.TripTrackPoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Stateless geometry for recorded tracks: jitter filtering, path length and Douglas-Peucker
 * simplification.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TrackGeometry {

    /** Fixes less accurate than this are dropped. */
    static final double MAX_ACCURACY_M = 100.0;

    /** A fix closer than max(its accuracy, this) to the previous kept fix is jitter. */
    static final double MIN_STEP_M = 5.0;

    /** Douglas-Peucker tolerance used for the stored polyline. */
    public static final double SIMPLIFY_TOLERANCE_M = 5.0;

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * Drops inaccurate fixes and fixes that did not move meaningfully from the previous kept one.
     *
     * @param points the track ordered by {@code recordedAt}
     * @return the kept coordinates, in order
     */
    public static List<LatLng> filterJitter(List<TripTrackPoint> points) {
        List<LatLng> kept = new ArrayList<>();
        for (TripTrackPoint p : points) {
            double accuracy = p.getAccuracyM() != null ? p.getAccuracyM() : 0.0;
            if (accuracy > MAX_ACCURACY_M) {
                continue;
            }
            LatLng fix = new LatLng(p.getLat(), p.getLon());
            if (!kept.isEmpty()
                    && metersBetween(kept.getLast(), fix) < Math.max(accuracy, MIN_STEP_M)) {
                continue;
            }
            kept.add(fix);
        }
        return kept;
    }

    /** Haversine length of the path in kilometers. */
    public static double distanceKm(List<LatLng> path) {
        double km = 0.0;
        for (int i = 1; i < path.size(); i++) {
            LatLng a = path.get(i - 1);
            LatLng b = path.get(i);
            km += HaversineDistanceStrategy.haversineKm(a.lat, a.lng, b.lat, b.lng);
        }
        return km;
    }

    /**
     * Douglas-Peucker simplification: keeps the fewest points such that no dropped point is more
     * than {@code toleranceM} from the simplified line. Iterative, so long tracks can't overflow
     * the stack.
     */
    public static List<LatLng> simplify(List<LatLng> path, double toleranceM) {
        int n = path.size();
        if (n < 3) {
            return path;
        }
        double cosLat = Math.cos(Math.toRadians(path.getFirst().lat));
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        Deque<int[]> ranges = new ArrayDeque<>();
        ranges.push(new int[] {0, n - 1});
        while (!ranges.isEmpty()) {
            int[] range = ranges.pop();
            int from = range[0];
            int to = range[1];
            double maxDistance = 0.0;
            int farthest = -1;
            for (int i = from + 1; i < to; i++) {
                double d = distanceToSegmentM(path.get(i), path.get(from), path.get(to), cosLat);
                if (d > maxDistance) {
                    maxDistance = d;
                    farthest = i;
                }
            }
            if (farthest != -1 && maxDistance > toleranceM) {
                keep[farthest] = true;
                ranges.push(new int[] {from, farthest});
                ranges.push(new int[] {farthest, to});
            }
        }
        List<LatLng> simplified = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                simplified.add(path.get(i));
            }
        }
        return simplified;
    }

    private static double metersBetween(LatLng a, LatLng b) {
        return HaversineDistanceStrategy.haversineKm(a.lat, a.lng, b.lat, b.lng) * 1000.0;
    }

    /** Distance from p to segment ab on a local flat projection (fine at track scale). */
    private static double distanceToSegmentM(LatLng p, LatLng a, LatLng b, double cosLat) {
        double ax = x(a, cosLat), ay = y(a), bx = x(b, cosLat), by = y(b);
        double px = x(p, cosLat), py = y(p);
        double dx = bx - ax, dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t =
                lengthSquared == 0
                        ? 0
                        : Math.max(
                                0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static double x(LatLng p, double cosLat) {
        return Math.toRadians(p.lng) * cosLat * EARTH_RADIUS_M;
    }

    private static double y(LatLng p) {
        return Math.toRadians(p.lat) * EARTH_RADIUS_M;
    }
}

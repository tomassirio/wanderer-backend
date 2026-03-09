package com.tomassirio.wanderer.commons.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle status of a trip.
 *
 * <ul>
 *   <li>{@link #CREATED} – Trip has been created but not yet started.
 *   <li>{@link #IN_PROGRESS} – Trip is actively underway.
 *   <li>{@link #PAUSED} – Trip has been temporarily paused mid-journey.
 *   <li>{@link #RESTING} – Pilgrim has completed the day's stage and is resting overnight before
 *       continuing the next day. Specific to multi-day trips.
 *   <li>{@link #FINISHED} – Trip has been completed.
 * </ul>
 */
public enum TripStatus {
    CREATED,
    IN_PROGRESS,
    PAUSED,
    RESTING,
    FINISHED;

    private static final Map<TripStatus, Set<TripStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(TripStatus.class);
        ALLOWED_TRANSITIONS.put(CREATED, EnumSet.of(IN_PROGRESS, FINISHED));
        ALLOWED_TRANSITIONS.put(IN_PROGRESS, EnumSet.of(PAUSED, RESTING, FINISHED));
        ALLOWED_TRANSITIONS.put(PAUSED, EnumSet.of(IN_PROGRESS, FINISHED));
        ALLOWED_TRANSITIONS.put(RESTING, EnumSet.of(IN_PROGRESS, FINISHED));
        ALLOWED_TRANSITIONS.put(FINISHED, EnumSet.noneOf(TripStatus.class));
    }

    /**
     * Returns whether transitioning from this status to the given target status is allowed.
     *
     * @param target the desired new status
     * @return {@code true} if the transition is permitted
     */
    public boolean canTransitionTo(TripStatus target) {
        Set<TripStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}

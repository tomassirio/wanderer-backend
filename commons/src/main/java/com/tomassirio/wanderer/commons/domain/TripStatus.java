package com.tomassirio.wanderer.commons.domain;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

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
@Getter
public enum TripStatus {
    CREATED(false),
    IN_PROGRESS(true),
    PAUSED(true),
    RESTING(true),
    FINISHED(false);

    private final boolean active;

    TripStatus(boolean active) {
        this.active = active;
    }

    /**
     * Returns an unmodifiable list of all statuses that represent an active (ongoing) trip.
     *
     * @return list of active {@link TripStatus} values
     */
    public static List<TripStatus> getActiveStatuses() {
        return Arrays.stream(values()).filter(TripStatus::isActive).toList();
    }

    /**
     * Statuses shown when browsing public trips: the active ones plus finished, so completed
     * adventures stay discoverable. Drafts (CREATED) are left out.
     *
     * @return active statuses and {@link #FINISHED}
     */
    public static List<TripStatus> getDiscoverableStatuses() {
        return Arrays.stream(values()).filter(s -> s.isActive() || s == FINISHED).toList();
    }

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
     * Returns whether a check-in (trip update) of the given type may be recorded on a trip in this
     * status. Live and paused trips accept any check-in. RESTING and FINISHED only accept the
     * lifecycle marker clients send right after the transition (DAY_END, TRIP_ENDED). Drafts
     * (CREATED) accept none.
     *
     * @param type the update type, {@code null} meaning {@link UpdateType#REGULAR}
     * @return {@code true} if the check-in is allowed
     */
    public boolean acceptsCheckIn(UpdateType type) {
        return switch (this) {
            case IN_PROGRESS, PAUSED -> true;
            case RESTING -> type == UpdateType.DAY_END;
            case FINISHED -> type == UpdateType.TRIP_ENDED;
            case CREATED -> false;
        };
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

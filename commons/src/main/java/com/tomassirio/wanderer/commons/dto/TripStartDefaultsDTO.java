package com.tomassirio.wanderer.commons.dto;

import com.tomassirio.wanderer.commons.domain.TripModality;
import com.tomassirio.wanderer.commons.domain.TripVisibility;

/**
 * Prefill values for the "ready to start" screen, taken from the user's last trip.
 *
 * @param fromLastTrip {@code false} when the user has no trips and these are the defaults
 */
public record TripStartDefaultsDTO(
        TripVisibility visibility,
        Boolean automaticUpdates,
        Integer updateRefresh,
        TripModality tripModality,
        boolean fromLastTrip) {

    public static final TripStartDefaultsDTO DEFAULTS =
            new TripStartDefaultsDTO(TripVisibility.PUBLIC, true, 900, TripModality.SIMPLE, false);
}

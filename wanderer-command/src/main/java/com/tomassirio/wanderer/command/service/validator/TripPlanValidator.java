package com.tomassirio.wanderer.command.service.validator;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Validator component for trip plan business rules. Handles validation of trip plan data such as
 * dates, locations, etc.
 *
 * @since 0.3.0
 */
@Component
public class TripPlanValidator {

    /**
     * Validates that the end date is not before the start date. A single-day plan starts and ends
     * on the same day.
     *
     * @param startDate the start date of the trip plan
     * @param endDate the end date of the trip plan
     * @throws IllegalArgumentException if the end date is before the start date
     */
    public void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
    }
}

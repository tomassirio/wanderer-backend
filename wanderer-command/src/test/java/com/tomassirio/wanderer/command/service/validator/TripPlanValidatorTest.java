package com.tomassirio.wanderer.command.service.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripPlanValidatorTest {

    private final TripPlanValidator validator = new TripPlanValidator();
    private final LocalDate day = LocalDate.of(2026, 10, 15);

    @Test
    void validateDates_whenSameDay_shouldPass() {
        assertThatCode(() -> validator.validateDates(day, day)).doesNotThrowAnyException();
    }

    @Test
    void validateDates_whenEndAfterStart_shouldPass() {
        assertThatCode(() -> validator.validateDates(day, day.plusDays(3)))
                .doesNotThrowAnyException();
    }

    @Test
    void validateDates_whenEndBeforeStart_shouldThrow() {
        assertThatThrownBy(() -> validator.validateDates(day, day.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must not be before start date");
    }
}

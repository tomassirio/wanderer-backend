package com.tomassirio.wanderer.commons.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TripTest {

    @Test
    void incrementUpdateCount_whenNull_shouldSetToOne() {
        // Given
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name("Test Trip")
                .userId(UUID.randomUUID())
                .updateCount(null)
                .build();

        // When
        trip.incrementUpdateCount();

        // Then
        assertThat(trip.getUpdateCount()).isEqualTo(1);
    }

    @Test
    void incrementUpdateCount_whenZero_shouldSetToOne() {
        // Given
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name("Test Trip")
                .userId(UUID.randomUUID())
                .updateCount(0)
                .build();

        // When
        trip.incrementUpdateCount();

        // Then
        assertThat(trip.getUpdateCount()).isEqualTo(1);
    }

    @Test
    void incrementUpdateCount_whenPositive_shouldIncrement() {
        // Given
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name("Test Trip")
                .userId(UUID.randomUUID())
                .updateCount(5)
                .build();

        // When
        trip.incrementUpdateCount();

        // Then
        assertThat(trip.getUpdateCount()).isEqualTo(6);
    }

    @Test
    void incrementUpdateCount_calledMultipleTimes_shouldIncrementEachTime() {
        // Given
        Trip trip = Trip.builder()
                .id(UUID.randomUUID())
                .name("Test Trip")
                .userId(UUID.randomUUID())
                .updateCount(null)
                .build();

        // When
        trip.incrementUpdateCount(); // null -> 1
        trip.incrementUpdateCount(); // 1 -> 2
        trip.incrementUpdateCount(); // 2 -> 3

        // Then
        assertThat(trip.getUpdateCount()).isEqualTo(3);
    }
}

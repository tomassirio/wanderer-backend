package com.tomassirio.wanderer.commons.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TripStatusTest {

    @Test
    void canTransitionTo_fromCreated_shouldAllowInProgressAndFinished() {
        assertThat(TripStatus.CREATED.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.CREATED.canTransitionTo(TripStatus.FINISHED)).isTrue();
    }

    @Test
    void canTransitionTo_fromCreated_shouldRejectPausedAndResting() {
        assertThat(TripStatus.CREATED.canTransitionTo(TripStatus.PAUSED)).isFalse();
        assertThat(TripStatus.CREATED.canTransitionTo(TripStatus.RESTING)).isFalse();
    }

    @Test
    void canTransitionTo_fromCreated_shouldRejectSelf() {
        assertThat(TripStatus.CREATED.canTransitionTo(TripStatus.CREATED)).isFalse();
    }

    @Test
    void canTransitionTo_fromInProgress_shouldAllowPausedRestingAndFinished() {
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.PAUSED)).isTrue();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.RESTING)).isTrue();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.FINISHED)).isTrue();
    }

    @Test
    void canTransitionTo_fromInProgress_shouldRejectCreated() {
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.CREATED)).isFalse();
    }

    @Test
    void canTransitionTo_fromPaused_shouldAllowInProgressAndFinished() {
        assertThat(TripStatus.PAUSED.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.PAUSED.canTransitionTo(TripStatus.FINISHED)).isTrue();
    }

    @Test
    void canTransitionTo_fromPaused_shouldRejectCreatedAndResting() {
        assertThat(TripStatus.PAUSED.canTransitionTo(TripStatus.CREATED)).isFalse();
        assertThat(TripStatus.PAUSED.canTransitionTo(TripStatus.RESTING)).isFalse();
    }

    @Test
    void canTransitionTo_fromResting_shouldAllowInProgressAndFinished() {
        assertThat(TripStatus.RESTING.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.RESTING.canTransitionTo(TripStatus.FINISHED)).isTrue();
    }

    @Test
    void canTransitionTo_fromResting_shouldRejectCreatedAndPaused() {
        assertThat(TripStatus.RESTING.canTransitionTo(TripStatus.CREATED)).isFalse();
        assertThat(TripStatus.RESTING.canTransitionTo(TripStatus.PAUSED)).isFalse();
    }

    @Test
    void canTransitionTo_fromFinished_shouldRejectAll() {
        for (TripStatus target : TripStatus.values()) {
            assertThat(TripStatus.FINISHED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void isActive_shouldReturnTrueForActiveStatuses() {
        assertThat(TripStatus.IN_PROGRESS.isActive()).isTrue();
        assertThat(TripStatus.PAUSED.isActive()).isTrue();
        assertThat(TripStatus.RESTING.isActive()).isTrue();
    }

    @Test
    void isActive_shouldReturnFalseForNonActiveStatuses() {
        assertThat(TripStatus.CREATED.isActive()).isFalse();
        assertThat(TripStatus.FINISHED.isActive()).isFalse();
    }

    @Test
    void getActiveStatuses_shouldReturnOnlyActiveStatuses() {
        List<TripStatus> activeStatuses = TripStatus.getActiveStatuses();

        assertThat(activeStatuses)
                .containsExactlyInAnyOrder(
                        TripStatus.IN_PROGRESS, TripStatus.PAUSED, TripStatus.RESTING);
        assertThat(activeStatuses).doesNotContain(TripStatus.CREATED, TripStatus.FINISHED);
    }
}

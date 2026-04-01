package com.tomassirio.wanderer.commons.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThumbnailUrlServiceTest {

    // --- generateThumbnailUrl ---

    @Test
    void generateThumbnailUrl_shouldReturnFormattedUrl() {
        UUID id = UUID.randomUUID();
        String result =
                ThumbnailUrlService.generateThumbnailUrl(
                        id, ThumbnailUrlService.ThumbnailType.TRIP);
        assertEquals("/thumbnails/trips/" + id + ".png", result);
    }

    @Test
    void generateThumbnailUrl_shouldReturnNull_whenEntityIdIsNull() {
        assertNull(
                ThumbnailUrlService.generateThumbnailUrl(
                        null, ThumbnailUrlService.ThumbnailType.TRIP));
    }

    @Test
    void generateThumbnailUrl_shouldUseCorrectPathForEachType() {
        UUID id = UUID.randomUUID();

        assertTrue(
                ThumbnailUrlService.generateThumbnailUrl(id, ThumbnailUrlService.ThumbnailType.TRIP)
                        .contains("/trips/"));
        assertTrue(
                ThumbnailUrlService.generateThumbnailUrl(
                                id, ThumbnailUrlService.ThumbnailType.TRIP_PLAN)
                        .contains("/plans/"));
        assertTrue(
                ThumbnailUrlService.generateThumbnailUrl(
                                id, ThumbnailUrlService.ThumbnailType.USER_PROFILE)
                        .contains("/profiles/"));
    }

    // --- generateTripThumbnailUrl ---

    @Test
    void generateTripThumbnailUrl_shouldReturnTripPath() {
        UUID tripId = UUID.randomUUID();
        assertEquals(
                "/thumbnails/trips/" + tripId + ".png",
                ThumbnailUrlService.generateTripThumbnailUrl(tripId));
    }

    @Test
    void generateTripThumbnailUrl_shouldReturnNull_whenNull() {
        assertNull(ThumbnailUrlService.generateTripThumbnailUrl(null));
    }

    // --- generateTripPlanThumbnailUrl ---

    @Test
    void generateTripPlanThumbnailUrl_shouldReturnPlanPath() {
        UUID planId = UUID.randomUUID();
        assertEquals(
                "/thumbnails/plans/" + planId + ".png",
                ThumbnailUrlService.generateTripPlanThumbnailUrl(planId));
    }

    @Test
    void generateTripPlanThumbnailUrl_shouldReturnNull_whenNull() {
        assertNull(ThumbnailUrlService.generateTripPlanThumbnailUrl(null));
    }

    // --- generateUserProfileThumbnailUrl ---

    @Test
    void generateUserProfileThumbnailUrl_shouldReturnProfilePath() {
        UUID userId = UUID.randomUUID();
        assertEquals(
                "/thumbnails/profiles/" + userId + ".png",
                ThumbnailUrlService.generateUserProfileThumbnailUrl(userId));
    }

    @Test
    void generateUserProfileThumbnailUrl_shouldReturnNull_whenNull() {
        assertNull(ThumbnailUrlService.generateUserProfileThumbnailUrl(null));
    }

    // --- resolveTripThumbnailUrl ---

    @Test
    void resolveTripThumbnailUrl_shouldReturnNull_whenTripIdIsNull() {
        assertNull(ThumbnailUrlService.resolveTripThumbnailUrl(null, true, null, null));
    }

    @Test
    void resolveTripThumbnailUrl_shouldReturnTripThumbnail_whenHasUpdates() {
        UUID tripId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(
                        tripId.toString(), true, planId.toString(), null);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldReturnPlanThumbnail_whenNoUpdatesAndHasPlan() {
        UUID tripId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(
                        tripId.toString(), false, planId.toString(), null);

        assertEquals("/thumbnails/plans/" + planId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldReturnTripThumbnail_whenNoUpdatesAndNoPlan() {
        UUID tripId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(tripId.toString(), false, null, null);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldReturnTripThumbnail_whenNoUpdatesAndEmptyPlanId() {
        UUID tripId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(tripId.toString(), false, "", null);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldReturnTripThumbnail_whenHasUpdatesAndNoPlan() {
        UUID tripId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(tripId.toString(), true, null, null);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldPreferTripThumbnail_whenHasUpdatesEvenWithPlan() {
        UUID tripId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        // Even though a plan exists, having updates means we use the trip thumbnail
        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(
                        tripId.toString(), true, planId.toString(), null);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldIncludeCacheBustingParameter_whenTimestampProvided() {
        UUID tripId = UUID.randomUUID();
        Long timestamp = 1234567890L;

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(
                        tripId.toString(), true, null, timestamp);

        assertEquals("/thumbnails/trips/" + tripId + ".png?v=" + timestamp, result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldNotIncludeCacheBustingParameter_whenTimestampIsNull() {
        UUID tripId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(tripId.toString(), true, null, null);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldNotIncludeCacheBustingParameter_whenTimestampIsZero() {
        UUID tripId = UUID.randomUUID();

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(tripId.toString(), true, null, 0L);

        assertEquals("/thumbnails/trips/" + tripId + ".png", result);
    }

    @Test
    void resolveTripThumbnailUrl_shouldIncludeCacheBustingOnPlanThumbnail_whenTimestampProvided() {
        UUID tripId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Long timestamp = 9876543210L;

        String result =
                ThumbnailUrlService.resolveTripThumbnailUrl(
                        tripId.toString(), false, planId.toString(), timestamp);

        assertEquals("/thumbnails/plans/" + planId + ".png?v=" + timestamp, result);
    }
}

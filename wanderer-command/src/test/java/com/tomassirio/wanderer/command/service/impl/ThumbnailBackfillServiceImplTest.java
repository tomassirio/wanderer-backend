package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.service.ThumbnailEntityType;
import com.tomassirio.wanderer.command.service.ThumbnailService;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.dto.ThumbnailBackfillResultDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThumbnailBackfillServiceImplTest {

    @Mock private TripRepository tripRepository;
    @Mock private ThumbnailService thumbnailService;

    @InjectMocks private ThumbnailBackfillServiceImpl service;

    @Test
    void regenerateMissingTripThumbnails_skipsExisting_countsRegeneratedAndFailed() {
        UUID hasFile = UUID.randomUUID();
        UUID regenerates = UUID.randomUUID();
        UUID throwsError = UUID.randomUUID();
        UUID silentlyFails = UUID.randomUUID();
        UUID vanished = UUID.randomUUID();
        Trip regeneratesTrip = Trip.builder().id(regenerates).build();
        Trip throwsTrip = Trip.builder().id(throwsError).build();
        Trip silentTrip = Trip.builder().id(silentlyFails).build();

        when(tripRepository.findIdsWithUpdates())
                .thenReturn(List.of(hasFile, throwsError, regenerates, silentlyFails, vanished));
        when(thumbnailService.thumbnailExists(hasFile, ThumbnailEntityType.TRIP)).thenReturn(true);
        // regenerates: missing before, present after
        when(thumbnailService.thumbnailExists(regenerates, ThumbnailEntityType.TRIP))
                .thenReturn(false, true);
        when(thumbnailService.thumbnailExists(throwsError, ThumbnailEntityType.TRIP))
                .thenReturn(false);
        // silentlyFails: generation swallows the error, file still missing afterwards
        when(thumbnailService.thumbnailExists(silentlyFails, ThumbnailEntityType.TRIP))
                .thenReturn(false, false);
        when(thumbnailService.thumbnailExists(vanished, ThumbnailEntityType.TRIP))
                .thenReturn(false);
        when(tripRepository.findById(regenerates)).thenReturn(Optional.of(regeneratesTrip));
        when(tripRepository.findById(throwsError)).thenReturn(Optional.of(throwsTrip));
        when(tripRepository.findById(silentlyFails)).thenReturn(Optional.of(silentTrip));
        when(tripRepository.findById(vanished)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Google down"))
                .when(thumbnailService)
                .generateAndSaveThumbnail(throwsTrip);

        ThumbnailBackfillResultDTO result = service.regenerateMissingTripThumbnails();

        assertThat(result).isEqualTo(new ThumbnailBackfillResultDTO(5, 4, 1, 3));
        verify(tripRepository, never()).findById(hasFile);
        // continued after the failure on the first missing trip
        verify(thumbnailService).generateAndSaveThumbnail(regeneratesTrip);
        verify(thumbnailService).generateAndSaveThumbnail(silentTrip);
    }

    @Test
    void regenerateMissingTripThumbnails_whenNoTrips_returnsZeros() {
        when(tripRepository.findIdsWithUpdates()).thenReturn(List.of());

        assertThat(service.regenerateMissingTripThumbnails())
                .isEqualTo(new ThumbnailBackfillResultDTO(0, 0, 0, 0));
        verify(thumbnailService, never()).generateAndSaveThumbnail(any(Trip.class));
    }
}

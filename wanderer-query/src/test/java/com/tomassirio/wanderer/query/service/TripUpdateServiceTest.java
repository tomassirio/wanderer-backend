package com.tomassirio.wanderer.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripUpdate;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.dto.TripUpdateDTO;
import com.tomassirio.wanderer.query.dto.TripUpdateLocationDTO;
import com.tomassirio.wanderer.query.repository.TripUpdateRepository;
import com.tomassirio.wanderer.query.service.impl.TripUpdateServiceImpl;
import com.tomassirio.wanderer.query.utils.TestEntityFactory;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TripUpdateServiceTest {

    @Mock private TripUpdateRepository tripUpdateRepository;

    @InjectMocks private TripUpdateServiceImpl tripUpdateService;

    @Test
    void getTripUpdate_whenTripUpdateExists_shouldReturnTripUpdateDTO() {
        // Given
        UUID tripUpdateId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");
        TripUpdate tripUpdate = TestEntityFactory.createTripUpdate(tripUpdateId, trip);

        when(tripUpdateRepository.findById(tripUpdateId)).thenReturn(Optional.of(tripUpdate));

        // When
        TripUpdateDTO result = tripUpdateService.getTripUpdate(tripUpdateId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(tripUpdateId.toString());
        assertThat(result.tripId()).isEqualTo(tripId.toString());
        assertThat(result.location()).isNotNull();
        assertThat(result.location().getLat()).isEqualTo(TestEntityFactory.LATITUDE);
        assertThat(result.location().getLon()).isEqualTo(TestEntityFactory.LONGITUDE);
        assertThat(result.battery()).isEqualTo(85);
        assertThat(result.message()).isEqualTo("Test update");
        assertThat(result.city()).isEqualTo("Santiago de Compostela");
        assertThat(result.country()).isEqualTo("Spain");
        assertThat(result.timestamp()).isNotNull();

        verify(tripUpdateRepository).findById(tripUpdateId);
    }

    @Test
    void getTripUpdate_whenTripUpdateDoesNotExist_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentTripUpdateId = UUID.randomUUID();
        when(tripUpdateRepository.findById(nonExistentTripUpdateId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripUpdateService.getTripUpdate(nonExistentTripUpdateId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip update not found");

        verify(tripUpdateRepository).findById(nonExistentTripUpdateId);
    }

    @Test
    void getTripUpdate_shouldMapLocationCorrectly() {
        // Given
        UUID tripUpdateId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate tripUpdate = TestEntityFactory.createTripUpdate(tripUpdateId, trip);

        when(tripUpdateRepository.findById(tripUpdateId)).thenReturn(Optional.of(tripUpdate));

        // When
        TripUpdateDTO result = tripUpdateService.getTripUpdate(tripUpdateId);

        // Then
        assertThat(result.location()).isNotNull();
        assertThat(result.location().getLat()).isEqualTo(TestEntityFactory.LATITUDE);
        assertThat(result.location().getLon()).isEqualTo(TestEntityFactory.LONGITUDE);
    }

    @Test
    void getTripUpdatesForTrip_whenTripUpdatesExist_shouldReturnPageOfTripUpdateDTOs() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");

        UUID updateId1 = UUID.randomUUID();
        UUID updateId2 = UUID.randomUUID();
        UUID updateId3 = UUID.randomUUID();

        TripUpdate update1 = TestEntityFactory.createTripUpdate(updateId1, trip);
        TripUpdate update2 = TestEntityFactory.createTripUpdate(updateId2, trip);
        TripUpdate update3 = TestEntityFactory.createTripUpdate(updateId3, trip);

        List<TripUpdate> tripUpdates = List.of(update1, update2, update3);

        when(tripUpdateRepository.findByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(tripUpdates, pageable, tripUpdates.size()));

        // When
        Page<TripUpdateDTO> result = tripUpdateService.getTripUpdatesForTrip(tripId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent().get(0).id()).isEqualTo(updateId1.toString());
        assertThat(result.getContent().get(1).id()).isEqualTo(updateId2.toString());
        assertThat(result.getContent().get(2).id()).isEqualTo(updateId3.toString());

        verify(tripUpdateRepository).findByTripId(tripId, pageable);
    }

    @Test
    void getTripUpdatesForTrip_whenNoTripUpdatesExist_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        when(tripUpdateRepository.findByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        // When
        Page<TripUpdateDTO> result = tripUpdateService.getTripUpdatesForTrip(tripId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(tripUpdateRepository).findByTripId(tripId, pageable);
    }

    @Test
    void getTripUpdatesForTrip_shouldReturnUpdatesOrderedByTimestampDescending() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);

        TripUpdate update1 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        TripUpdate update2 = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);

        // Repository should return in descending order
        List<TripUpdate> orderedUpdates = List.of(update2, update1);

        when(tripUpdateRepository.findByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(orderedUpdates, pageable, orderedUpdates.size()));

        // When
        Page<TripUpdateDTO> result = tripUpdateService.getTripUpdatesForTrip(tripId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        // Verify the order is maintained
        assertThat(result.getContent().get(0).id()).isEqualTo(update2.getId().toString());
        assertThat(result.getContent().get(1).id()).isEqualTo(update1.getId().toString());

        verify(tripUpdateRepository).findByTripId(tripId, pageable);
    }

    @Test
    void getTripUpdatesForTrip_shouldMapAllFieldsCorrectly() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate tripUpdate = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);

        when(tripUpdateRepository.findByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(List.of(tripUpdate), pageable, 1));

        // When
        Page<TripUpdateDTO> result = tripUpdateService.getTripUpdatesForTrip(tripId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        TripUpdateDTO dto = result.getContent().get(0);
        assertThat(dto.id()).isNotNull();
        assertThat(dto.tripId()).isEqualTo(tripId.toString());
        assertThat(dto.location()).isNotNull();
        assertThat(dto.battery()).isEqualTo(85);
        assertThat(dto.message()).isEqualTo("Test update");
        assertThat(dto.city()).isEqualTo("Santiago de Compostela");
        assertThat(dto.country()).isEqualTo("Spain");
        assertThat(dto.timestamp()).isNotNull();
    }

    @Test
    void getTripUpdateLocations_whenUpdatesExist_shouldReturnLocationDTOs() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);

        UUID updateId1 = UUID.randomUUID();
        UUID updateId2 = UUID.randomUUID();
        TripUpdate update1 = TestEntityFactory.createTripUpdate(updateId1, trip);
        update1.setUpdateType(UpdateType.DAY_START);
        TripUpdate update2 = TestEntityFactory.createTripUpdate(updateId2, trip);
        update2.setUpdateType(UpdateType.REGULAR);

        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update1, update2));

        // When
        List<TripUpdateLocationDTO> result = tripUpdateService.getTripUpdateLocations(tripId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(updateId1.toString());
        assertThat(result.get(0).lat()).isEqualTo(TestEntityFactory.LATITUDE);
        assertThat(result.get(0).lon()).isEqualTo(TestEntityFactory.LONGITUDE);
        assertThat(result.get(0).timestamp()).isNotNull();
        assertThat(result.get(0).updateType()).isEqualTo(UpdateType.DAY_START);
        assertThat(result.get(0).battery()).isEqualTo(85);
        assertThat(result.get(0).city()).isEqualTo("Santiago de Compostela");
        assertThat(result.get(0).country()).isEqualTo("Spain");

        assertThat(result.get(1).id()).isEqualTo(updateId2.toString());
        assertThat(result.get(1).updateType()).isEqualTo(UpdateType.REGULAR);

        verify(tripUpdateRepository).findByTripIdOrderByTimestampAsc(tripId);
    }

    @Test
    void getTripUpdateLocations_whenNoUpdatesExist_shouldReturnEmptyList() {
        // Given
        UUID tripId = UUID.randomUUID();
        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(Collections.emptyList());

        // When
        List<TripUpdateLocationDTO> result = tripUpdateService.getTripUpdateLocations(tripId);

        // Then
        assertThat(result).isEmpty();

        verify(tripUpdateRepository).findByTripIdOrderByTimestampAsc(tripId);
    }

    @Test
    void getTripUpdateLocations_whenLocationIsNull_shouldReturnNullLatLon() {
        // Given
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId);
        TripUpdate update = TestEntityFactory.createTripUpdate(UUID.randomUUID(), trip);
        update.setLocation(null);

        when(tripUpdateRepository.findByTripIdOrderByTimestampAsc(tripId))
                .thenReturn(List.of(update));

        // When
        List<TripUpdateLocationDTO> result = tripUpdateService.getTripUpdateLocations(tripId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).lat()).isNull();
        assertThat(result.get(0).lon()).isNull();
    }
}

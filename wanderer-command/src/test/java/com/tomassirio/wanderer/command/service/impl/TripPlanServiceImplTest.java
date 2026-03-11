package com.tomassirio.wanderer.command.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.command.controller.request.TripPlanCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripPlanUpdateRequest;
import com.tomassirio.wanderer.command.event.TripPlanCreatedEvent;
import com.tomassirio.wanderer.command.event.TripPlanDeletedEvent;
import com.tomassirio.wanderer.command.event.TripPlanUpdatedEvent;
import com.tomassirio.wanderer.command.repository.TripPlanRepository;
import com.tomassirio.wanderer.command.service.TripPlanMetadataProcessor;
import com.tomassirio.wanderer.command.service.validator.OwnershipValidator;
import com.tomassirio.wanderer.command.service.validator.TripPlanValidator;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TripPlanServiceImplTest {

    @Mock private TripPlanRepository tripPlanRepository;

    @Mock private TripPlanMetadataProcessor metadataProcessor;

    @Mock private OwnershipValidator ownershipValidator;

    @Mock private TripPlanValidator tripPlanValidator;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TripPlanServiceImpl tripPlanService;

    private UUID userId;
    private UUID planId;
    private LocalDate startDate;
    private LocalDate endDate;
    private GeoLocation startLocation;
    private GeoLocation endLocation;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        planId = UUID.randomUUID();
        startDate = LocalDate.now().plusDays(1);
        endDate = LocalDate.now().plusDays(7);
        startLocation = GeoLocation.builder().lat(40.7128).lon(-74.0060).build();
        endLocation = GeoLocation.builder().lat(34.0522).lon(-118.2437).build();
    }

    // CREATE TRIP PLAN TESTS

    @Test
    void createTripPlan_whenValidSimpleRequest_shouldPublishEventAndReturnId() {
        // Given
        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Europe Summer Trip",
                        startDate,
                        endDate,
                        startLocation,
                        endLocation,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        // When
        UUID result = tripPlanService.createTripPlan(userId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripPlanCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripPlanCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripPlanCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getTripPlanId()).isEqualTo(result);
        assertThat(event.getName()).isEqualTo("Europe Summer Trip");
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getPlanType()).isEqualTo(TripPlanType.SIMPLE);
        assertThat(event.getStartDate()).isEqualTo(startDate);
        assertThat(event.getEndDate()).isEqualTo(endDate);
        assertThat(event.getStartLocation()).isEqualTo(startLocation);
        assertThat(event.getEndLocation()).isEqualTo(endLocation);

        verify(metadataProcessor).applyMetadata(any(TripPlan.class), any());
    }

    @Test
    void createTripPlan_whenMultiDayRequest_shouldPublishEventWithMultiDayType() {
        // Given
        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Multi-Day Adventure",
                        startDate,
                        endDate,
                        startLocation,
                        endLocation,
                        List.of(),
                        TripPlanType.MULTI_DAY,
                        null);

        // When
        UUID result = tripPlanService.createTripPlan(userId, request);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<TripPlanCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripPlanCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue().getPlanType()).isEqualTo(TripPlanType.MULTI_DAY);
    }

    @Test
    void createTripPlan_whenInvalidDates_shouldThrowException() {
        // Given
        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Invalid Plan",
                        endDate,
                        startDate,
                        startLocation,
                        endLocation,
                        List.of(),
                        TripPlanType.SIMPLE,
                        null);

        doThrow(new IllegalArgumentException("End date must be after start date"))
                .when(tripPlanValidator)
                .validateDates(endDate, startDate);

        // When & Then
        assertThatThrownBy(() -> tripPlanService.createTripPlan(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must be after start date");

        verify(eventPublisher, never()).publishEvent(any(TripPlanCreatedEvent.class));
    }

    @Test
    void createTripPlan_shouldApplyMetadataCorrectly() {
        // Given
        TripPlanCreationRequest request =
                new TripPlanCreationRequest(
                        "Metadata Test",
                        startDate,
                        endDate,
                        startLocation,
                        endLocation,
                        List.of(),
                        TripPlanType.MULTI_DAY,
                        null);

        // When
        UUID result = tripPlanService.createTripPlan(userId, request);

        // Then
        verify(metadataProcessor).applyMetadata(any(TripPlan.class), any());

        ArgumentCaptor<TripPlanCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripPlanCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripPlanCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getMetadata()).isNotNull();
    }

    // UPDATE TRIP PLAN TESTS

    @Test
    void updateTripPlan_whenValidRequest_shouldPublishEventAndReturnId() {
        // Given
        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "Updated Plan Name",
                        startDate.plusDays(1),
                        endDate.plusDays(1),
                        startLocation,
                        endLocation,
                        List.of(),
                        null);

        TripPlan existingPlan =
                TripPlan.builder()
                        .id(planId)
                        .name("Original Name")
                        .planType(TripPlanType.SIMPLE)
                        .userId(userId)
                        .createdTimestamp(Instant.now())
                        .startDate(startDate)
                        .endDate(endDate)
                        .startLocation(startLocation)
                        .endLocation(endLocation)
                        .metadata(new HashMap<>())
                        .build();

        when(tripPlanRepository.findById(planId)).thenReturn(Optional.of(existingPlan));

        // When
        UUID result = tripPlanService.updateTripPlan(userId, planId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(planId);

        verify(ownershipValidator)
                .validateOwnership(eq(existingPlan), eq(userId), any(), any(), eq("trip plan"));

        ArgumentCaptor<TripPlanUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripPlanUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripPlanUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getTripPlanId()).isEqualTo(planId);
        assertThat(event.getName()).isEqualTo("Updated Plan Name");
    }

    @Test
    void updateTripPlan_whenPlanNotFound_shouldThrowEntityNotFoundException() {
        // Given
        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "Updated Plan",
                        startDate,
                        endDate,
                        startLocation,
                        endLocation,
                        List.of(),
                        null);

        when(tripPlanRepository.findById(planId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripPlanService.updateTripPlan(userId, planId, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip plan not found");

        verify(tripPlanRepository).findById(planId);
        verify(ownershipValidator, never()).validateOwnership(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(TripPlanUpdatedEvent.class));
    }

    @Test
    void updateTripPlan_whenUserDoesNotOwnPlan_shouldThrowAccessDeniedException() {
        // Given
        UUID differentUserId = UUID.randomUUID();
        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "Updated Plan",
                        startDate,
                        endDate,
                        startLocation,
                        endLocation,
                        List.of(),
                        null);

        TripPlan existingPlan =
                TripPlan.builder()
                        .id(planId)
                        .name("Original Name")
                        .planType(TripPlanType.SIMPLE)
                        .userId(differentUserId)
                        .createdTimestamp(Instant.now())
                        .startDate(startDate)
                        .endDate(endDate)
                        .startLocation(startLocation)
                        .endLocation(endLocation)
                        .metadata(new HashMap<>())
                        .build();

        when(tripPlanRepository.findById(planId)).thenReturn(Optional.of(existingPlan));
        doThrow(new AccessDeniedException("User does not have permission to modify trip plan"))
                .when(ownershipValidator)
                .validateOwnership(any(), any(), any(), any(), any());

        // When & Then
        assertThatThrownBy(() -> tripPlanService.updateTripPlan(userId, planId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission");

        verify(tripPlanRepository).findById(planId);
        verify(ownershipValidator)
                .validateOwnership(any(), eq(userId), any(), any(), eq("trip plan"));
        verify(eventPublisher, never()).publishEvent(any(TripPlanUpdatedEvent.class));
    }

    @Test
    void updateTripPlan_shouldPublishEventWithAllUpdatedFields() {
        // Given
        GeoLocation newStartLocation = GeoLocation.builder().lat(51.5074).lon(-0.1278).build();
        GeoLocation newEndLocation = GeoLocation.builder().lat(48.8566).lon(2.3522).build();
        LocalDate newStartDate = startDate.plusDays(5);
        LocalDate newEndDate = endDate.plusDays(5);

        TripPlanUpdateRequest request =
                new TripPlanUpdateRequest(
                        "Completely Updated Plan",
                        newStartDate,
                        newEndDate,
                        newStartLocation,
                        newEndLocation,
                        List.of(),
                        null);

        TripPlan existingPlan =
                TripPlan.builder()
                        .id(planId)
                        .name("Original Name")
                        .planType(TripPlanType.SIMPLE)
                        .userId(userId)
                        .createdTimestamp(Instant.now())
                        .startDate(startDate)
                        .endDate(endDate)
                        .startLocation(startLocation)
                        .endLocation(endLocation)
                        .metadata(new HashMap<>())
                        .build();

        when(tripPlanRepository.findById(planId)).thenReturn(Optional.of(existingPlan));

        // When
        tripPlanService.updateTripPlan(userId, planId, request);

        // Then
        ArgumentCaptor<TripPlanUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripPlanUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripPlanUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getName()).isEqualTo("Completely Updated Plan");
        assertThat(event.getStartDate()).isEqualTo(newStartDate);
        assertThat(event.getEndDate()).isEqualTo(newEndDate);
        assertThat(event.getStartLocation()).isEqualTo(newStartLocation);
        assertThat(event.getEndLocation()).isEqualTo(newEndLocation);
    }

    // DELETE TRIP PLAN TESTS

    @Test
    void deleteTripPlan_whenPlanExists_shouldPublishDeleteEvent() {
        // Given
        TripPlan existingPlan =
                TripPlan.builder()
                        .id(planId)
                        .name("Plan to Delete")
                        .planType(TripPlanType.SIMPLE)
                        .userId(userId)
                        .createdTimestamp(Instant.now())
                        .startDate(startDate)
                        .endDate(endDate)
                        .startLocation(startLocation)
                        .endLocation(endLocation)
                        .metadata(new HashMap<>())
                        .build();

        when(tripPlanRepository.findById(planId)).thenReturn(Optional.of(existingPlan));

        // When
        tripPlanService.deleteTripPlan(userId, planId);

        // Then
        verify(tripPlanRepository).findById(planId);
        verify(ownershipValidator)
                .validateOwnership(eq(existingPlan), eq(userId), any(), any(), eq("trip plan"));

        ArgumentCaptor<TripPlanDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripPlanDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TripPlanDeletedEvent event = eventCaptor.getValue();
        assertThat(event.getTripPlanId()).isEqualTo(planId);
        assertThat(event.getUserId()).isEqualTo(userId);
    }

    @Test
    void deleteTripPlan_whenPlanNotFound_shouldThrowEntityNotFoundException() {
        // Given
        when(tripPlanRepository.findById(planId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> tripPlanService.deleteTripPlan(userId, planId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Trip plan not found");

        verify(tripPlanRepository).findById(planId);
        verify(ownershipValidator, never()).validateOwnership(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(TripPlanDeletedEvent.class));
    }

    @Test
    void deleteTripPlan_whenUserDoesNotOwnPlan_shouldThrowAccessDeniedException() {
        // Given
        UUID differentUserId = UUID.randomUUID();
        TripPlan existingPlan =
                TripPlan.builder()
                        .id(planId)
                        .name("Plan to Delete")
                        .planType(TripPlanType.SIMPLE)
                        .userId(differentUserId)
                        .createdTimestamp(Instant.now())
                        .startDate(startDate)
                        .endDate(endDate)
                        .startLocation(startLocation)
                        .endLocation(endLocation)
                        .metadata(new HashMap<>())
                        .build();

        when(tripPlanRepository.findById(planId)).thenReturn(Optional.of(existingPlan));
        doThrow(new AccessDeniedException("User does not have permission to delete trip plan"))
                .when(ownershipValidator)
                .validateOwnership(any(), any(), any(), any(), any());

        // When & Then
        assertThatThrownBy(() -> tripPlanService.deleteTripPlan(userId, planId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission");

        verify(tripPlanRepository).findById(planId);
        verify(ownershipValidator)
                .validateOwnership(any(), eq(userId), any(), any(), eq("trip plan"));
        verify(eventPublisher, never()).publishEvent(any(TripPlanDeletedEvent.class));
    }
}

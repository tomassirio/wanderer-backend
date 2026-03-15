package com.tomassirio.wanderer.command.utils;

import com.tomassirio.wanderer.command.controller.request.TripCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripPlanCreationRequest;
import com.tomassirio.wanderer.command.controller.request.TripPlanUpdateRequest;
import com.tomassirio.wanderer.command.controller.request.TripUpdateRequest;
import com.tomassirio.wanderer.commons.domain.GeoLocation;
import com.tomassirio.wanderer.commons.domain.TripPlanType;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory;
import java.time.LocalDate;
import java.util.List;

/**
 * Test entity factory for wanderer-command module. Extends the commons TestEntityFactory with
 * command-specific DTOs.
 */
public class TestEntityFactory extends BaseTestEntityFactory {

    // TripCreationRequest factory methods
    public static TripCreationRequest createTripCreationRequest(String name) {
        return createTripCreationRequest(name, TripVisibility.PUBLIC);
    }

    public static TripCreationRequest createTripCreationRequest(
            String name, TripVisibility tripVisibility) {
        return new TripCreationRequest(name, tripVisibility, null, null, null);
    }

    // TripUpdateRequest factory methods
    public static TripUpdateRequest createTripUpdateRequest(String name) {
        return createTripUpdateRequest(name, TripVisibility.PUBLIC);
    }

    public static TripUpdateRequest createTripUpdateRequest(
            String name, TripVisibility tripVisibility) {
        return new TripUpdateRequest(name, tripVisibility);
    }

    // TripPlanCreationRequest factory methods
    public static TripPlanCreationRequest createTripPlanCreationRequest(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            GeoLocation startLocation,
            GeoLocation endLocation,
            TripPlanType planType) {
        return new TripPlanCreationRequest(
                name, startDate, endDate, startLocation, endLocation, List.of(), planType, null);
    }

    public static TripPlanCreationRequest createTripPlanCreationRequest(String name) {
        return createTripPlanCreationRequest(
                name,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(7),
                createGeoLocation(40.7128, -74.0060), // New York
                createGeoLocation(34.0522, -118.2437), // Los Angeles
                TripPlanType.SIMPLE);
    }

    // TripPlanUpdateRequest factory methods
    public static TripPlanUpdateRequest createTripPlanUpdateRequest(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            GeoLocation startLocation,
            GeoLocation endLocation) {
        return new TripPlanUpdateRequest(
                name, startDate, endDate, startLocation, endLocation, List.of(), null);
    }

    public static TripPlanUpdateRequest createTripPlanUpdateRequest(String name) {
        return createTripPlanUpdateRequest(
                name,
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(8),
                createGeoLocation(51.5074, -0.1278), // London
                createGeoLocation(48.8566, 2.3522)); // Paris
    }

    // GeoLocation factory method
    public static GeoLocation createGeoLocation(Double lat, Double lon) {
        return GeoLocation.builder().lat(lat).lon(lon).build();
    }
}

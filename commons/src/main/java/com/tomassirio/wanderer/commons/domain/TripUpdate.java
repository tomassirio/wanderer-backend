package com.tomassirio.wanderer.commons.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "trip_updates")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TripUpdate {

    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Type(JsonBinaryType.class)
    @Column(name = "location", columnDefinition = "jsonb")
    private GeoLocation location;

    @Column(name = "battery")
    private Integer battery;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Type(JsonBinaryType.class)
    @Column(name = "reactions", columnDefinition = "jsonb")
    private Reactions reactions;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_condition")
    private WeatherCondition weatherCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_type")
    private UpdateType updateType;

    @NotNull
    @Column(nullable = false)
    private Instant timestamp;
}

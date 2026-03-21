package com.tomassirio.wanderer.commons.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "trip_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripPlan implements Polylineable {

    @Id private UUID id;

    @NotBlank
    @Size(min = 3, max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private TripPlanType planType;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "created_timestamp", nullable = false)
    private Instant createdTimestamp;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Column(name = "start_location", columnDefinition = "jsonb", nullable = false)
    @Type(JsonBinaryType.class)
    private GeoLocation startLocation;

    @NotNull
    @Column(name = "end_location", columnDefinition = "jsonb", nullable = false)
    @Type(JsonBinaryType.class)
    private GeoLocation endLocation;

    @Type(JsonBinaryType.class)
    @Column(name = "waypoints", columnDefinition = "jsonb")
    private List<GeoLocation> waypoints;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "encoded_polyline", columnDefinition = "TEXT")
    private String encodedPolyline;

    @Column(name = "planned_polyline", columnDefinition = "TEXT")
    private String plannedPolyline;

    @Column(name = "polyline_updated_at")
    private Instant polylineUpdatedAt;
}

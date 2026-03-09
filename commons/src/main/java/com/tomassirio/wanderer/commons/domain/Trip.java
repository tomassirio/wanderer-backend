package com.tomassirio.wanderer.commons.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "trips")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip implements Polylineable {

    @Id private UUID id;

    @NotNull
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Embedded private TripSettings tripSettings;

    @Embedded private TripDetails tripDetails;

    @Column(name = "trip_plan_id")
    private UUID tripPlanId; // Optional reference to a trip plan

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @SQLRestriction("parent_comment_id IS NULL")
    private List<Comment> comments;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TripUpdate> tripUpdates;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TripDay> tripDays;

    @Column(name = "encoded_polyline", columnDefinition = "TEXT")
    private String encodedPolyline;

    @Column(name = "polyline_updated_at")
    private Instant polylineUpdatedAt;

    @NotNull
    @Column(name = "creation_timestamp", nullable = false)
    private Instant creationTimestamp;

    @NotNull
    @Column(nullable = false)
    private Boolean enabled;
}

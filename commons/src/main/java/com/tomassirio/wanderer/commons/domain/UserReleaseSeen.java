package com.tomassirio.wanderer.commons.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Highest release version a user has seen the "What's new" notes for. One row per user, shared by
 * all their clients.
 *
 * @since 1.3.0
 */
@Entity
@Table(name = "user_release_seen")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserReleaseSeen {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_seen_version", nullable = false)
    private String lastSeenVersion;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

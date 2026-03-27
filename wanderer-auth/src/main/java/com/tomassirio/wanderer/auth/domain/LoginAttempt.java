package com.tomassirio.wanderer.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity tracking failed login attempts for brute-force protection.
 */
@Entity
@Table(name = "login_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "success", nullable = false)
    private boolean success;

    @CreationTimestamp
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;
}

package com.tomassirio.wanderer.auth.domain;

import com.tomassirio.wanderer.commons.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Null for SSO-only accounts. Password registration always sets it (see withPassword). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "roles", length = 1000)
    @Convert(converter = RolesConverter.class)
    private Set<Role> roles = new HashSet<>();
}

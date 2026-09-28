package com.tomassirio.wanderer.auth.repository;

import com.tomassirio.wanderer.auth.domain.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {
    Optional<Credential> findByEmail(String email);

    /**
     * Checks if any credentials exist with roles containing the specified role string. Uses a
     * native query because the roles field is stored as a comma-separated String via a JPA
     * converter, and Spring Data JPA's derived query methods cannot handle this.
     *
     * @param role the role string to search for (e.g., "ADMIN")
     * @return true if at least one credential has the role, false otherwise
     */
    @Query(
            value =
                    "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END "
                            + "FROM user_credentials "
                            + "WHERE roles LIKE CONCAT('%', :role, '%')",
            nativeQuery = true)
    boolean existsByRolesContaining(@Param("role") String role);
}

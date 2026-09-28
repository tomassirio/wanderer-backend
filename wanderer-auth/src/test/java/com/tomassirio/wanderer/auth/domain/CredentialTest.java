package com.tomassirio.wanderer.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tomassirio.wanderer.commons.security.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void withPassword_setsHashRoleAndEnabled() {
        Credential credential = Credential.withPassword(userId, "a@b.com", "$2a$hash");

        assertEquals("$2a$hash", credential.getPasswordHash());
        assertTrue(credential.hasPassword());
        assertTrue(credential.isEnabled());
        assertEquals(Set.of(Role.USER), credential.getRoles());
    }

    @Test
    void withPassword_rejectsNullHash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Credential.withPassword(userId, "a@b.com", null));
    }

    @Test
    void withPassword_rejectsBlankHash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Credential.withPassword(userId, "a@b.com", "  "));
    }

    @Test
    void ssoOnly_hasNoPassword() {
        Credential credential = Credential.ssoOnly(userId, "a@b.com");

        assertNull(credential.getPasswordHash());
        assertFalse(credential.hasPassword());
        assertTrue(credential.isEnabled());
        assertEquals(Set.of(Role.USER), credential.getRoles());
    }
}

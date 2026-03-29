package com.tomassirio.wanderer.auth.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import feign.FeignException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailLookupStrategyTest {

    @Mock private CredentialRepository credentialRepository;

    @Mock private WandererQueryClient wandererQueryClient;

    @InjectMocks private EmailLookupStrategy strategy;

    private UUID userId;
    private UserBasicInfo testUserInfo;
    private Credential testCredential;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUserInfo = new UserBasicInfo(userId, "testuser");
        testCredential =
                Credential.builder()
                        .userId(userId)
                        .email("test@example.com")
                        .passwordHash("hashedPassword")
                        .enabled(true)
                        .build();
    }

    @Test
    void canHandle_whenIdentifierContainsAt_shouldReturnTrue() {
        assertTrue(strategy.canHandle("user@example.com"));
        assertTrue(strategy.canHandle("test@domain.co.uk"));
    }

    @Test
    void canHandle_whenIdentifierDoesNotContainAt_shouldReturnFalse() {
        assertFalse(strategy.canHandle("username"));
        assertFalse(strategy.canHandle("john_doe"));
    }

    @Test
    void canHandle_whenIdentifierIsNull_shouldReturnFalse() {
        assertFalse(strategy.canHandle(null));
    }

    @Test
    void lookupUser_whenEmailExists_shouldReturnUser() {
        String email = "test@example.com";
        when(credentialRepository.findByEmail(email)).thenReturn(Optional.of(testCredential));
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(testUserInfo);

        Optional<User> result = strategy.lookupUser(email);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
        assertEquals("testuser", result.get().getUsername());
        verify(credentialRepository).findByEmail(email);
        verify(wandererQueryClient).getUserById(userId, "basic");
    }

    @Test
    void lookupUser_whenEmailNotFound_shouldReturnEmpty() {
        String email = "notfound@example.com";
        when(credentialRepository.findByEmail(email)).thenReturn(Optional.empty());

        Optional<User> result = strategy.lookupUser(email);

        assertFalse(result.isPresent());
        verify(credentialRepository).findByEmail(email);
        verify(wandererQueryClient, never()).getUserById(any(), any());
    }

    @Test
    void lookupUser_whenUserServiceFails_shouldReturnEmpty() {
        String email = "test@example.com";
        when(credentialRepository.findByEmail(email)).thenReturn(Optional.of(testCredential));
        when(wandererQueryClient.getUserById(userId, "basic")).thenThrow(FeignException.NotFound.class);

        Optional<User> result = strategy.lookupUser(email);

        assertFalse(result.isPresent());
        verify(credentialRepository).findByEmail(email);
        verify(wandererQueryClient).getUserById(userId, "basic");
    }
}

package com.tomassirio.wanderer.auth.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.commons.domain.User;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsernameLookupStrategyTest {

    @Mock
    private WandererQueryClient wandererQueryClient;

    @InjectMocks
    private UsernameLookupStrategy strategy;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .username("testuser")
                .build();
    }

    @Test
    void canHandle_whenIdentifierDoesNotContainAt_shouldReturnTrue() {
        assertTrue(strategy.canHandle("username"));
        assertTrue(strategy.canHandle("john_doe"));
        assertTrue(strategy.canHandle("user123"));
    }

    @Test
    void canHandle_whenIdentifierContainsAt_shouldReturnFalse() {
        assertFalse(strategy.canHandle("user@example.com"));
        assertFalse(strategy.canHandle("test@domain.com"));
    }

    @Test
    void canHandle_whenIdentifierIsNull_shouldReturnFalse() {
        assertFalse(strategy.canHandle(null));
    }

    @Test
    void lookupUser_whenUsernameExists_shouldReturnUser() {
        String username = "TestUser";
        String normalizedUsername = "testuser";
        when(wandererQueryClient.getUserByUsername(normalizedUsername)).thenReturn(testUser);

        Optional<User> result = strategy.lookupUser(username);

        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(wandererQueryClient).getUserByUsername(normalizedUsername);
    }

    @Test
    void lookupUser_whenUsernameNotFound_shouldReturnEmpty() {
        String username = "nonexistent";
        Request mockRequest = Request.create(
                Request.HttpMethod.GET,
                "/users/username/nonexistent",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        when(wandererQueryClient.getUserByUsername(username))
                .thenThrow(new FeignException.NotFound("Not found", mockRequest, null, null));

        Optional<User> result = strategy.lookupUser(username);

        assertFalse(result.isPresent());
        verify(wandererQueryClient).getUserByUsername(username);
    }

    @Test
    void lookupUser_whenServiceError_shouldThrowIllegalStateException() {
        String username = "testuser";
        Request mockRequest = Request.create(
                Request.HttpMethod.GET,
                "/users/username/testuser",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        when(wandererQueryClient.getUserByUsername(username))
                .thenThrow(new FeignException.InternalServerError("Server error", mockRequest, null, null));

        assertThrows(IllegalStateException.class, () -> strategy.lookupUser(username));
        verify(wandererQueryClient).getUserByUsername(username);
    }

    @Test
    void lookupUser_shouldNormalizeUsernameToLowercase() {
        String username = "TestUser";
        String expectedNormalized = "testuser";
        when(wandererQueryClient.getUserByUsername(expectedNormalized)).thenReturn(testUser);

        strategy.lookupUser(username);

        verify(wandererQueryClient).getUserByUsername(expectedNormalized);
    }
}

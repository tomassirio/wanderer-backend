package com.tomassirio.wanderer.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.auth.client.WandererCommandClient;
import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import com.tomassirio.wanderer.auth.domain.Credential;
import com.tomassirio.wanderer.auth.repository.CredentialRepository;
import com.tomassirio.wanderer.auth.service.impl.UserProvisioningServiceImpl;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.UserBasicInfo;
import com.tomassirio.wanderer.commons.security.Role;
import feign.FeignException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceImplTest {

    @Mock private CredentialRepository credentialRepository;
    @Mock private WandererCommandClient wandererCommandClient;
    @Mock private WandererQueryClient wandererQueryClient;
    @InjectMocks private UserProvisioningServiceImpl provisioningService;

    private final UUID userId = UUID.randomUUID();
    private final UserBasicInfo info = new UserBasicInfo(userId, "testuser");

    @Test
    void provisionWithPassword_createsUserAndSavesCredentialWithHash() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        when(wandererCommandClient.createUser(payload.capture())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(info);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        User user =
                provisioningService.provisionWithPassword(
                        "testuser", "t@e.com", "TestUser", "$2a$hash");

        assertEquals(userId, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("testuser", payload.getValue().get("username"));
        assertEquals("TestUser", payload.getValue().get("displayName"));
        assertEquals("t@e.com", payload.getValue().get("email"));
        ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(saved.capture());
        assertEquals("$2a$hash", saved.getValue().getPasswordHash());
        assertEquals(Set.of(Role.USER), saved.getValue().getRoles());
    }

    @Test
    void provisionWithPassword_rejectsNullHashBeforeAnyRemoteCall() {
        assertThrows(
                IllegalArgumentException.class,
                () -> provisioningService.provisionWithPassword("u", "e@x.com", "u", null));
        verifyNoInteractions(wandererCommandClient, wandererQueryClient, credentialRepository);
    }

    @Test
    void provisionWithPassword_rejectsBlankHashBeforeAnyRemoteCall() {
        assertThrows(
                IllegalArgumentException.class,
                () -> provisioningService.provisionWithPassword("u", "e@x.com", "u", " "));
        verifyNoInteractions(wandererCommandClient, wandererQueryClient, credentialRepository);
    }

    @Test
    void provisionWithoutPassword_savesCredentialWithoutHash() {
        when(wandererCommandClient.createUser(any())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(info);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());

        provisioningService.provisionWithoutPassword("testuser", "t@e.com", "Test User");

        ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(saved.capture());
        assertNull(saved.getValue().getPasswordHash());
    }

    @Test
    void whenCreateUserFails_throwsAndSavesNothing() {
        when(wandererCommandClient.createUser(any())).thenThrow(FeignException.class);

        assertThrows(
                IllegalStateException.class,
                () -> provisioningService.provisionWithoutPassword("u", "e@x.com", "u"));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void whenFetchUserFails_deletesCreatedUserAndThrows() {
        when(wandererCommandClient.createUser(any())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenThrow(FeignException.class);

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> provisioningService.provisionWithoutPassword("u", "e@x.com", "u"));

        assertEquals("Failed to fetch created user from query service", ex.getMessage());
        verify(wandererCommandClient).deleteUser(userId);
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void whenCredentialSaveFails_deletesCreatedUserAndThrows() {
        when(wandererCommandClient.createUser(any())).thenReturn(userId);
        when(wandererQueryClient.getUserById(userId, "basic")).thenReturn(info);
        when(credentialRepository.findById(userId)).thenReturn(Optional.empty());
        when(credentialRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThrows(
                IllegalStateException.class,
                () -> provisioningService.provisionWithoutPassword("u", "e@x.com", "u"));
        verify(wandererCommandClient).deleteUser(userId);
    }
}

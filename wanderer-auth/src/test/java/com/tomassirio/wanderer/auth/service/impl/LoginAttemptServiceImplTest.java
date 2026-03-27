package com.tomassirio.wanderer.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.tomassirio.wanderer.auth.domain.LoginAttempt;
import com.tomassirio.wanderer.auth.repository.LoginAttemptRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

    private UUID userId;
    private String identifier;
    private String ipAddress;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        identifier = "testuser";
        ipAddress = "192.168.1.1";
        
        // Set default config values
        ReflectionTestUtils.setField(loginAttemptService, "maxFailedAttempts", 10);
        ReflectionTestUtils.setField(loginAttemptService, "lockoutDurationMinutes", 15);
        ReflectionTestUtils.setField(loginAttemptService, "attemptWindowMinutes", 30);
    }

    @Test
    void recordSuccessfulLogin_shouldSaveAttempt() {
        loginAttemptService.recordSuccessfulLogin(identifier, userId, ipAddress);

        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(captor.capture());

        LoginAttempt saved = captor.getValue();
        assertEquals(identifier, saved.getIdentifier());
        assertEquals(userId, saved.getUserId());
        assertEquals(ipAddress, saved.getIpAddress());
        assertTrue(saved.isSuccess());
    }

    @Test
    void recordFailedLogin_shouldSaveAttempt() {
        loginAttemptService.recordFailedLogin(identifier, ipAddress);

        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(captor.capture());

        LoginAttempt saved = captor.getValue();
        assertEquals(identifier, saved.getIdentifier());
        assertNull(saved.getUserId());
        assertEquals(ipAddress, saved.getIpAddress());
        assertFalse(saved.isSuccess());
    }

    @Test
    void isAccountLocked_whenFailedAttemptsExceedLimit_shouldReturnTrue() {
        when(loginAttemptRepository.countRecentFailedAttempts(eq(identifier), any(Instant.class)))
                .thenReturn(10);

        boolean result = loginAttemptService.isAccountLocked(identifier);

        assertTrue(result);
    }

    @Test
    void isAccountLocked_whenFailedAttemptsBelowLimit_shouldReturnFalse() {
        when(loginAttemptRepository.countRecentFailedAttempts(eq(identifier), any(Instant.class)))
                .thenReturn(5);

        boolean result = loginAttemptService.isAccountLocked(identifier);

        assertFalse(result);
    }

    @Test
    void isAccountLocked_whenNoFailedAttempts_shouldReturnFalse() {
        when(loginAttemptRepository.countRecentFailedAttempts(eq(identifier), any(Instant.class)))
                .thenReturn(0);

        boolean result = loginAttemptService.isAccountLocked(identifier);

        assertFalse(result);
    }

    @Test
    void cleanupOldAttempts_shouldDeleteOldRecords() {
        int deletedCount = 100;
        when(loginAttemptRepository.deleteOldAttempts(any(Instant.class)))
                .thenReturn(deletedCount);

        int result = loginAttemptService.cleanupOldAttempts();

        assertEquals(deletedCount, result);
        verify(loginAttemptRepository).deleteOldAttempts(any(Instant.class));
    }
}

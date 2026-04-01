package com.tomassirio.wanderer.command.websocket.auth;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of a WebSocket authentication attempt.
 */
@Getter
@AllArgsConstructor
public class AuthenticationResult {
    
    /**
     * The authenticated user ID. Null for anonymous users.
     */
    private final UUID userId;
    
    /**
     * Whether the authentication was successful.
     */
    private final boolean authenticated;
    
    /**
     * Whether this is an anonymous connection.
     */
    private final boolean anonymous;
    
    /**
     * Optional error message if authentication failed.
     */
    private final String errorMessage;
    
    /**
     * Creates a successful authenticated result.
     */
    public static AuthenticationResult authenticated(UUID userId) {
        return new AuthenticationResult(userId, true, false, null);
    }
    
    /**
     * Creates a successful anonymous result.
     */
    public static AuthenticationResult anonymous() {
        return new AuthenticationResult(null, true, true, null);
    }
    
    /**
     * Creates a failed authentication result.
     */
    public static AuthenticationResult failed(String errorMessage) {
        return new AuthenticationResult(null, false, false, errorMessage);
    }
}

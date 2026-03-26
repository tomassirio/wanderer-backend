package com.tomassirio.wanderer.auth.dto;

import com.tomassirio.wanderer.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Token is required") String token,
        @NotBlank(message = "New password is required") @ValidPassword String newPassword) {}

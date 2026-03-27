package com.tomassirio.wanderer.auth.dto;

import com.tomassirio.wanderer.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New password is required") @ValidPassword String newPassword) {}

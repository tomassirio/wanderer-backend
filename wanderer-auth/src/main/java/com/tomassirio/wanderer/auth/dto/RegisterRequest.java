package com.tomassirio.wanderer.auth.dto;

import com.tomassirio.wanderer.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid")
                String email,
        @NotBlank(message = "Password is required") @ValidPassword String password) {}

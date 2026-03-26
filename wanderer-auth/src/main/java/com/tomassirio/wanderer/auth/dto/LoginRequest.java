package com.tomassirio.wanderer.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username or email is required") String identifier,
        @NotBlank(message = "password is required")
                @Size(min = 8, message = "password must be at least 8 characters")
                String password) {}

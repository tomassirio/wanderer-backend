package com.tomassirio.wanderer.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username or email is required") String identifier,
        @NotBlank(message = "password is required") String password) {}

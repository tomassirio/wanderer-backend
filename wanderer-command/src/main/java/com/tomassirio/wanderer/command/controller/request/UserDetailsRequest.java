package com.tomassirio.wanderer.command.controller.request;

import jakarta.validation.constraints.Size;

public record UserDetailsRequest(
        @Size(max = 100, message = "Display name must not exceed 100 characters")
                String displayName,
        @Size(max = 500, message = "Bio must not exceed 500 characters") String bio) {}

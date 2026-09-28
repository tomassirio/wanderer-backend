package com.tomassirio.wanderer.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoExchangeRequest(@NotBlank(message = "Code is required") String code) {}

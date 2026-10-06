package com.tomassirio.wanderer.command.controller.request;

import jakarta.validation.constraints.NotBlank;

public record ReleaseSeenRequest(@NotBlank(message = "version is required") String version) {}

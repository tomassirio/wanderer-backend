package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.Release;
import com.tomassirio.wanderer.commons.domain.Release.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** CI request to write and publish the notes for a released version in one go. */
public record ReleasePublishRequest(
        @NotBlank(message = "version is required") String version,
        @NotBlank(message = "headline is required") @Size(max = Release.MAX_TITLE) String headline,
        @NotEmpty(message = "items is required") List<@Valid @NotNull Item> items) {}

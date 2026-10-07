package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.Release;
import com.tomassirio.wanderer.commons.domain.Release.Item;
import com.tomassirio.wanderer.commons.domain.Release.PlatformRelease;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Full replace of a release's editable fields (admin). */
public record ReleaseUpdateRequest(
        @Size(max = Release.MAX_TITLE) String headline,
        @NotNull(message = "showPopup is required") Boolean showPopup,
        @NotNull(message = "platforms is required") List<@Valid @NotNull PlatformRelease> platforms,
        @NotNull(message = "items is required") List<@Valid @NotNull Item> items) {}

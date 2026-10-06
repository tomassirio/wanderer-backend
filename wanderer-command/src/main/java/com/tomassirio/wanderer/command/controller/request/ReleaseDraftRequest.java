package com.tomassirio.wanderer.command.controller.request;

import com.tomassirio.wanderer.commons.domain.Release;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** CI request to create or update the draft for a tagged version. */
public record ReleaseDraftRequest(
        @NotBlank(message = "version is required") String version,
        List<Release.Platform> platforms,
        @NotNull(message = "prs is required") List<@Valid @NotNull PullRequest> prs) {

    public record PullRequest(
            @NotNull(message = "PR number is required") Integer number,
            @NotBlank(message = "PR title is required") String title,
            String label,
            String summary) {}
}

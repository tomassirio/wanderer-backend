package com.tomassirio.wanderer.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.dto.UserDetailsDTO;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.util.UUID;

public record UserResponse(UUID id, String username, UserDetailsDTO userDetails) {

    @JsonProperty("avatarUrl")
    public String avatarUrl() {
        return id != null ? ThumbnailUrlService.generateUserProfileThumbnailUrl(id) : null;
    }
}

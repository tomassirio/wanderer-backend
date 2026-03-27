package com.tomassirio.wanderer.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tomassirio.wanderer.commons.dto.UserDetailsDTO;
import com.tomassirio.wanderer.commons.service.ThumbnailUrlService;
import java.util.UUID;

public record UserRelationshipResponse(
        UUID id,
        String username,
        UserDetailsDTO userDetails,
        boolean isFriend,
        boolean isFollowing,
        boolean isFollower) {

    @JsonProperty("avatarUrl")
    public String avatarUrl() {
        if (id == null) {
            return null;
        }
        return ThumbnailUrlService.generateUserProfileThumbnailUrl(id);
    }
}

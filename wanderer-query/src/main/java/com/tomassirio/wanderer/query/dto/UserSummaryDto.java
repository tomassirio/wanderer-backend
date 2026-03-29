package com.tomassirio.wanderer.query.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for User entity containing only basic information.
 * Used for list views and references to reduce data transfer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDto {
    private UUID id;
    private String username;
    private String displayName;
    private String profilePictureUrl;
}

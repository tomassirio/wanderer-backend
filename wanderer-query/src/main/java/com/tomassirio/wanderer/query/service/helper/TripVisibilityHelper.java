package com.tomassirio.wanderer.query.service.helper;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.query.repository.FriendshipRepository;
import com.tomassirio.wanderer.query.repository.TripRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Enforces trip visibility on per-trip reads: PUBLIC for everyone (including anonymous), PROTECTED
 * for the owner and their friends, PRIVATE for the owner only. Admins see everything. Same
 * friendship rule as {@code TripServiceImpl#getTripsForUserWithVisibility}.
 */
@Component
@RequiredArgsConstructor
public class TripVisibilityHelper {

    private final TripRepository tripRepository;
    private final FriendshipRepository friendshipRepository;

    /**
     * @throws EntityNotFoundException if the trip does not exist
     * @throws AccessDeniedException if the requester may not see the trip
     */
    public void assertCanView(UUID tripId, UUID requestingUserId) {
        Trip trip =
                tripRepository
                        .findById(tripId)
                        .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        TripVisibility visibility =
                trip.getTripSettings() != null ? trip.getTripSettings().getVisibility() : null;

        if (visibility == TripVisibility.PUBLIC
                || trip.getUserId().equals(requestingUserId)
                || isAdmin()) {
            return;
        }
        if (visibility == TripVisibility.PROTECTED
                && requestingUserId != null
                && friendshipRepository.existsByUserIdAndFriendId(
                        requestingUserId, trip.getUserId())) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this trip");
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

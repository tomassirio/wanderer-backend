package com.tomassirio.wanderer.command.handler;

import com.tomassirio.wanderer.command.event.AchievementUnlockedEvent;
import com.tomassirio.wanderer.command.event.CommentAddedEvent;
import com.tomassirio.wanderer.command.event.CommentReactionEvent;
import com.tomassirio.wanderer.command.event.FriendRequestAcceptedEvent;
import com.tomassirio.wanderer.command.event.FriendRequestDeclinedEvent;
import com.tomassirio.wanderer.command.event.FriendRequestSentEvent;
import com.tomassirio.wanderer.command.event.TripStatusChangedEvent;
import com.tomassirio.wanderer.command.event.TripUpdatedEvent;
import com.tomassirio.wanderer.command.event.UserFollowedEvent;
import com.tomassirio.wanderer.command.repository.CommentRepository;
import com.tomassirio.wanderer.command.repository.FriendshipRepository;
import com.tomassirio.wanderer.command.repository.NotificationRepository;
import com.tomassirio.wanderer.command.repository.TripRepository;
import com.tomassirio.wanderer.command.repository.UserFollowRepository;
import com.tomassirio.wanderer.command.repository.UserRepository;
import com.tomassirio.wanderer.commons.domain.Comment;
import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.Notification;
import com.tomassirio.wanderer.commons.domain.NotificationType;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Centralized event listener that creates persisted {@link Notification} records for relevant
 * domain events asynchronously.
 *
 * <p>Notifications are stored in the database so they survive WebSocket disconnections and can be
 * queried later via the notification query endpoints.
 *
 * <p>Processing is asynchronous and occurs after transaction commit to avoid blocking the main
 * request flow. This improves response times for actions that trigger notifications.
 *
 * <p>Self-notification suppression: no notification is created when the actor is the same as the
 * recipient.
 *
 * @author tomassirio
 * @since 0.10.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final FriendshipRepository friendshipRepository;
    private final CommentRepository commentRepository;

    // ==================== FRIEND REQUEST EVENTS ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onFriendRequestSent(FriendRequestSentEvent event) {
        log.debug("Creating notification for FriendRequestSentEvent: {}", event.getRequestId());

        String actorName = resolveUsername(event.getSenderId());

        save(
                Notification.builder()
                        .id(UUID.randomUUID())
                        .recipientId(event.getReceiverId())
                        .actorId(event.getSenderId())
                        .type(NotificationType.FRIEND_REQUEST_RECEIVED)
                        .referenceId(event.getRequestId())
                        .message(actorName + " sent you a friend request")
                        .createdAt(Instant.now())
                        .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onFriendRequestAccepted(FriendRequestAcceptedEvent event) {
        log.debug("Creating notification for FriendRequestAcceptedEvent: {}", event.getRequestId());

        String actorName = resolveUsername(event.getReceiverId());

        save(
                Notification.builder()
                        .id(UUID.randomUUID())
                        .recipientId(event.getSenderId())
                        .actorId(event.getReceiverId())
                        .type(NotificationType.FRIEND_REQUEST_ACCEPTED)
                        .referenceId(event.getRequestId())
                        .message(actorName + " accepted your friend request")
                        .createdAt(Instant.now())
                        .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onFriendRequestDeclined(FriendRequestDeclinedEvent event) {
        log.debug("Creating notification for FriendRequestDeclinedEvent: {}", event.getRequestId());

        save(
                Notification.builder()
                        .id(UUID.randomUUID())
                        .recipientId(event.getSenderId())
                        .actorId(event.getReceiverId())
                        .type(NotificationType.FRIEND_REQUEST_DECLINED)
                        .referenceId(event.getRequestId())
                        .message("Your friend request was declined")
                        .createdAt(Instant.now())
                        .build());
    }

    // ==================== COMMENT EVENTS ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCommentAdded(CommentAddedEvent event) {
        log.debug("Creating notification for CommentAddedEvent: {}", event.getCommentId());

        String actorName =
                event.getUsername() != null
                        ? event.getUsername()
                        : resolveUsername(event.getUserId());

        // Notify trip owner (if not the commenter)
        Optional<Trip> tripOpt = tripRepository.findById(event.getTripId());
        tripOpt.ifPresent(
                trip -> {
                    if (!trip.getUserId().equals(event.getUserId())) {
                        save(
                                Notification.builder()
                                        .id(UUID.randomUUID())
                                        .recipientId(trip.getUserId())
                                        .actorId(event.getUserId())
                                        .type(NotificationType.COMMENT_ON_TRIP)
                                        .referenceId(event.getTripId())
                                        .message(
                                                actorName
                                                        + " commented on your trip \""
                                                        + trip.getName()
                                                        + "\"")
                                        .createdAt(Instant.now())
                                        .build());
                    }
                });

        // If it's a reply, also notify the parent comment author
        if (event.getParentCommentId() != null) {
            Optional<Comment> parentOpt = commentRepository.findById(event.getParentCommentId());
            parentOpt.ifPresent(
                    parent -> {
                        UUID parentAuthorId = parent.getUser().getId();
                        // Skip if replying to own comment or if parent author is trip owner
                        // (already notified)
                        if (!parentAuthorId.equals(event.getUserId())
                                && tripOpt.map(trip -> !parentAuthorId.equals(trip.getUserId()))
                                        .orElse(true)) {
                            save(
                                    Notification.builder()
                                            .id(UUID.randomUUID())
                                            .recipientId(parentAuthorId)
                                            .actorId(event.getUserId())
                                            .type(NotificationType.REPLY_TO_COMMENT)
                                            .referenceId(event.getParentCommentId())
                                            .message(actorName + " replied to your comment")
                                            .createdAt(Instant.now())
                                            .build());
                        }
                    });
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCommentReaction(CommentReactionEvent event) {
        if (!event.isAdded()) {
            return; // Only notify on reaction addition, not removal
        }

        log.debug(
                "Creating notification for CommentReactionEvent on comment: {}",
                event.getCommentId());

        Optional<Comment> commentOpt = commentRepository.findById(event.getCommentId());
        commentOpt.ifPresent(
                comment -> {
                    UUID commentAuthorId = comment.getUser().getId();

                    // Skip self-reaction
                    if (!commentAuthorId.equals(event.getUserId())) {
                        String actorName = resolveUsername(event.getUserId());

                        save(
                                Notification.builder()
                                        .id(UUID.randomUUID())
                                        .recipientId(commentAuthorId)
                                        .actorId(event.getUserId())
                                        .type(NotificationType.COMMENT_REACTION)
                                        .referenceId(event.getCommentId())
                                        .message(actorName + " reacted to your comment")
                                        .createdAt(Instant.now())
                                        .build());
                    }
                });
    }

    // ==================== USER FOLLOW EVENTS ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onUserFollowed(UserFollowedEvent event) {
        log.debug("Creating notification for UserFollowedEvent: {}", event.getFollowId());

        String actorName = resolveUsername(event.getFollowerId());

        save(
                Notification.builder()
                        .id(UUID.randomUUID())
                        .recipientId(event.getFollowedId())
                        .actorId(event.getFollowerId())
                        .type(NotificationType.NEW_FOLLOWER)
                        .referenceId(event.getFollowerId())
                        .message(actorName + " started following you")
                        .createdAt(Instant.now())
                        .build());
    }

    // ==================== ACHIEVEMENT EVENTS ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onAchievementUnlocked(AchievementUnlockedEvent event) {
        log.debug(
                "Creating notification for AchievementUnlockedEvent: {}",
                event.getUserAchievementId());

        save(
                Notification.builder()
                        .id(UUID.randomUUID())
                        .recipientId(event.getUserId())
                        .actorId(null)
                        .type(NotificationType.ACHIEVEMENT_UNLOCKED)
                        .referenceId(event.getAchievementId())
                        .message(
                                "You unlocked the achievement \""
                                        + event.getAchievementName()
                                        + "\"!")
                        .createdAt(Instant.now())
                        .build());
    }

    // ==================== TRIP LIFECYCLE EVENTS ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onTripStatusChanged(TripStatusChangedEvent event) {
        String newStatus = event.getNewStatus();
        if (!TripStatus.IN_PROGRESS.name().equals(newStatus)
                && !TripStatus.FINISHED.name().equals(newStatus)) {
            return; // Only notify for major status transitions
        }

        log.debug(
                "Creating notifications for TripStatusChangedEvent: trip={}, status={}",
                event.getTripId(),
                newStatus);

        Optional<Trip> tripOpt = tripRepository.findById(event.getTripId());
        tripOpt.ifPresent(
                trip -> {
                    TripVisibility visibility = trip.getTripSettings().getVisibility();
                    if (visibility == TripVisibility.PRIVATE) {
                        log.debug("Skipping notifications for private trip {}", event.getTripId());
                        return;
                    }

                    UUID ownerId = trip.getUserId();
                    String ownerName = resolveUsername(ownerId);
                    String statusLabel =
                            TripStatus.IN_PROGRESS.name().equals(newStatus)
                                    ? "started"
                                    : "finished";
                    String message =
                            ownerName
                                    + " "
                                    + statusLabel
                                    + " their trip \""
                                    + trip.getName()
                                    + "\"";

                    Set<UUID> recipientIds = collectRecipientsByVisibility(ownerId, visibility);
                    saveForRecipients(
                            recipientIds,
                            ownerId,
                            NotificationType.TRIP_STATUS_CHANGED,
                            event.getTripId(),
                            message);
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onTripUpdated(TripUpdatedEvent event) {
        // Only notify for regular updates that include a message
        if (event.getMessage() == null || event.getMessage().isBlank()) {
            return;
        }
        if (event.getUpdateType() != null && event.getUpdateType() != UpdateType.REGULAR) {
            return; // System-generated updates (DAY_START, etc.) don't warrant extra notifications
        }

        log.debug("Creating notifications for TripUpdatedEvent: trip={}", event.getTripId());

        Optional<Trip> tripOpt = tripRepository.findById(event.getTripId());
        tripOpt.ifPresent(
                trip -> {
                    TripVisibility visibility = trip.getTripSettings().getVisibility();
                    if (visibility == TripVisibility.PRIVATE) {
                        log.debug("Skipping notifications for private trip {}", event.getTripId());
                        return;
                    }

                    UUID ownerId = trip.getUserId();
                    String ownerName = resolveUsername(ownerId);
                    String message = ownerName + " posted an update on \"" + trip.getName() + "\"";

                    Set<UUID> recipientIds = collectRecipientsByVisibility(ownerId, visibility);
                    saveForRecipients(
                            recipientIds,
                            ownerId,
                            NotificationType.TRIP_UPDATE_POSTED,
                            event.getTripId(),
                            message);
                });
    }

    // ==================== HELPERS ====================

    /**
     * Collects recipient user IDs based on trip visibility.
     *
     * <ul>
     *   <li>{@code PRIVATE} — no recipients (caller should skip before reaching this method)
     *   <li>{@code PROTECTED} — friends only
     *   <li>{@code PUBLIC} — followers + friends
     * </ul>
     *
     * @param userId the trip owner whose followers/friends to find
     * @param visibility the trip's visibility setting
     * @return set of unique recipient user IDs (excludes the user themselves)
     */
    private Set<UUID> collectRecipientsByVisibility(UUID userId, TripVisibility visibility) {
        Set<UUID> recipients = new HashSet<>();

        // Friends are notified for both PROTECTED and PUBLIC trips
        friendshipRepository.findByUserId(userId).stream()
                .map(Friendship::getFriendId)
                .forEach(recipients::add);

        // Followers are only notified for PUBLIC trips
        if (visibility == TripVisibility.PUBLIC) {
            userFollowRepository.findByFollowedId(userId).stream()
                    .map(UserFollow::getFollowerId)
                    .forEach(recipients::add);
        }

        // Never include the user themselves
        recipients.remove(userId);
        return recipients;
    }

    /** Creates and saves notifications for multiple recipients in a batch. */
    private void saveForRecipients(
            Set<UUID> recipientIds,
            UUID actorId,
            NotificationType type,
            UUID referenceId,
            String message) {
        Instant now = Instant.now();
        List<Notification> notifications = new ArrayList<>(recipientIds.size());

        for (UUID recipientId : recipientIds) {
            notifications.add(
                    Notification.builder()
                            .id(UUID.randomUUID())
                            .recipientId(recipientId)
                            .actorId(actorId)
                            .type(type)
                            .referenceId(referenceId)
                            .message(message)
                            .createdAt(now)
                            .build());
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
            log.info(
                    "Created {} {} notifications for reference {}",
                    notifications.size(),
                    type,
                    referenceId);
        }
    }

    private void save(Notification notification) {
        notificationRepository.save(notification);
        log.debug(
                "Notification created: type={}, recipient={}",
                notification.getType(),
                notification.getRecipientId());
    }

    private String resolveUsername(UUID userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("Someone");
    }
}

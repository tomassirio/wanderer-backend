package com.tomassirio.wanderer.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.tomassirio.wanderer.commons.domain.AchievementType;
import com.tomassirio.wanderer.commons.domain.Comment;
import com.tomassirio.wanderer.commons.domain.Friendship;
import com.tomassirio.wanderer.commons.domain.Notification;
import com.tomassirio.wanderer.commons.domain.NotificationType;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.TripStatus;
import com.tomassirio.wanderer.commons.domain.TripSettings;
import com.tomassirio.wanderer.commons.domain.TripVisibility;
import com.tomassirio.wanderer.commons.domain.UpdateType;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.domain.UserFollow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private CommentRepository commentRepository;

    @InjectMocks private NotificationEventListener listener;

    private UUID senderId;
    private UUID receiverId;
    private UUID tripOwnerId;
    private UUID tripId;
    private UUID commentId;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        tripOwnerId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        commentId = UUID.randomUUID();
    }

    // ==================== FRIEND REQUEST TESTS ====================

    @Test
    void onFriendRequestSent_CreatesNotificationForReceiver() {
        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(User.builder().id(senderId).username("alice").build()));

        FriendRequestSentEvent event =
                FriendRequestSentEvent.builder()
                        .requestId(UUID.randomUUID())
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .status("PENDING")
                        .createdAt(Instant.now())
                        .build();

        listener.onFriendRequestSent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(receiverId);
        assertThat(saved.getActorId()).isEqualTo(senderId);
        assertThat(saved.getType()).isEqualTo(NotificationType.FRIEND_REQUEST_RECEIVED);
        assertThat(saved.getMessage()).contains("alice");
    }

    @Test
    void onFriendRequestAccepted_CreatesNotificationForSender() {
        when(userRepository.findById(receiverId))
                .thenReturn(
                        Optional.of(User.builder().id(receiverId).username("bob").build()));

        FriendRequestAcceptedEvent event =
                FriendRequestAcceptedEvent.builder()
                        .requestId(UUID.randomUUID())
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .build();

        listener.onFriendRequestAccepted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(senderId);
        assertThat(saved.getType()).isEqualTo(NotificationType.FRIEND_REQUEST_ACCEPTED);
        assertThat(saved.getMessage()).contains("bob");
    }

    @Test
    void onFriendRequestDeclined_CreatesNotificationForSender() {
        FriendRequestDeclinedEvent event =
                FriendRequestDeclinedEvent.builder()
                        .requestId(UUID.randomUUID())
                        .senderId(senderId)
                        .receiverId(receiverId)
                        .build();

        listener.onFriendRequestDeclined(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(senderId);
        assertThat(saved.getType()).isEqualTo(NotificationType.FRIEND_REQUEST_DECLINED);
    }

    // ==================== COMMENT TESTS ====================

    @Test
    void onCommentAdded_NotifiesTripOwner() {
        Trip trip = Trip.builder().id(tripId).userId(tripOwnerId).name("My Trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        CommentAddedEvent event =
                CommentAddedEvent.builder()
                        .commentId(commentId)
                        .tripId(tripId)
                        .userId(senderId)
                        .username("alice")
                        .message("Great trip!")
                        .parentCommentId(null)
                        .timestamp(Instant.now())
                        .build();

        listener.onCommentAdded(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(tripOwnerId);
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT_ON_TRIP);
        assertThat(saved.getMessage()).contains("alice").contains("My Trip");
    }

    @Test
    void onCommentAdded_SkipsNotificationWhenCommenterIsTripOwner() {
        Trip trip = Trip.builder().id(tripId).userId(senderId).name("My Trip").build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        CommentAddedEvent event =
                CommentAddedEvent.builder()
                        .commentId(commentId)
                        .tripId(tripId)
                        .userId(senderId) // same as trip owner
                        .username("owner")
                        .message("My own comment")
                        .parentCommentId(null)
                        .timestamp(Instant.now())
                        .build();

        listener.onCommentAdded(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void onCommentAdded_Reply_NotifiesParentCommentAuthor() {
        UUID parentAuthorId = UUID.randomUUID();
        Trip trip = Trip.builder().id(tripId).userId(tripOwnerId).name("My Trip").build();
        User parentAuthor = User.builder().id(parentAuthorId).username("parentUser").build();
        Comment parentComment =
                Comment.builder()
                        .id(UUID.randomUUID())
                        .user(parentAuthor)
                        .trip(trip)
                        .message("parent")
                        .timestamp(Instant.now())
                        .build();

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(commentRepository.findById(any())).thenReturn(Optional.of(parentComment));

        CommentAddedEvent event =
                CommentAddedEvent.builder()
                        .commentId(commentId)
                        .tripId(tripId)
                        .userId(senderId)
                        .username("alice")
                        .message("Reply!")
                        .parentCommentId(parentComment.getId())
                        .timestamp(Instant.now())
                        .build();

        listener.onCommentAdded(event);

        // Should create 2 notifications: one for trip owner, one for parent comment author
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // ==================== COMMENT REACTION TESTS ====================

    @Test
    void onCommentReaction_Added_NotifiesCommentAuthor() {
        UUID commentAuthorId = UUID.randomUUID();
        User commentAuthor =
                User.builder().id(commentAuthorId).username("commentAuthor").build();
        Comment comment =
                Comment.builder()
                        .id(commentId)
                        .user(commentAuthor)
                        .message("a comment")
                        .timestamp(Instant.now())
                        .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(User.builder().id(senderId).username("reactor").build()));

        CommentReactionEvent event =
                CommentReactionEvent.builder()
                        .tripId(tripId)
                        .commentId(commentId)
                        .reactionType("HEART")
                        .userId(senderId)
                        .added(true)
                        .build();

        listener.onCommentReaction(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.COMMENT_REACTION);
        assertThat(captor.getValue().getRecipientId()).isEqualTo(commentAuthorId);
    }

    @Test
    void onCommentReaction_Removed_DoesNotNotify() {
        CommentReactionEvent event =
                CommentReactionEvent.builder()
                        .tripId(tripId)
                        .commentId(commentId)
                        .reactionType("HEART")
                        .userId(senderId)
                        .added(false)
                        .build();

        listener.onCommentReaction(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void onCommentReaction_SelfReaction_DoesNotNotify() {
        User commentAuthor = User.builder().id(senderId).username("self").build();
        Comment comment =
                Comment.builder()
                        .id(commentId)
                        .user(commentAuthor)
                        .message("a comment")
                        .timestamp(Instant.now())
                        .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        CommentReactionEvent event =
                CommentReactionEvent.builder()
                        .tripId(tripId)
                        .commentId(commentId)
                        .reactionType("HEART")
                        .userId(senderId) // same as comment author
                        .added(true)
                        .build();

        listener.onCommentReaction(event);

        verify(notificationRepository, never()).save(any());
    }

    // ==================== USER FOLLOW TESTS ====================

    @Test
    void onUserFollowed_NotifiesFollowedUser() {
        when(userRepository.findById(senderId))
                .thenReturn(Optional.of(User.builder().id(senderId).username("follower").build()));

        UserFollowedEvent event =
                UserFollowedEvent.builder()
                        .followId(UUID.randomUUID())
                        .followerId(senderId)
                        .followedId(receiverId)
                        .createdAt(Instant.now())
                        .build();

        listener.onUserFollowed(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(receiverId);
        assertThat(saved.getType()).isEqualTo(NotificationType.NEW_FOLLOWER);
        assertThat(saved.getMessage()).contains("follower");
    }

    // ==================== ACHIEVEMENT TESTS ====================

    @Test
    void onAchievementUnlocked_NotifiesUser() {
        AchievementUnlockedEvent event =
                AchievementUnlockedEvent.builder()
                        .userAchievementId(UUID.randomUUID())
                        .userId(senderId)
                        .achievementId(UUID.randomUUID())
                        .tripId(tripId)
                        .achievementType(AchievementType.DISTANCE_100KM)
                        .achievementName("First Century")
                        .valueAchieved(100.0)
                        .unlockedAt(Instant.now())
                        .build();

        listener.onAchievementUnlocked(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(senderId);
        assertThat(saved.getActorId()).isNull();
        assertThat(saved.getType()).isEqualTo(NotificationType.ACHIEVEMENT_UNLOCKED);
        assertThat(saved.getMessage()).contains("First Century");
    }

    // ==================== TRIP STATUS CHANGED TESTS ====================

    @Test
    void onTripStatusChanged_PublicTrip_NotifiesFollowersAndFriends() {
        TripSettings settings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(tripOwnerId)
                        .name("Camino")
                        .tripSettings(settings)
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(tripOwnerId))
                .thenReturn(
                        Optional.of(
                                User.builder().id(tripOwnerId).username("walker").build()));

        UUID followerId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        when(userFollowRepository.findByFollowedId(tripOwnerId))
                .thenReturn(
                        List.of(
                                UserFollow.builder()
                                        .id(UUID.randomUUID())
                                        .followerId(followerId)
                                        .followedId(tripOwnerId)
                                        .createdAt(Instant.now())
                                        .build()));
        when(friendshipRepository.findByUserId(tripOwnerId))
                .thenReturn(
                        List.of(
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(tripOwnerId)
                                        .friendId(friendId)
                                        .createdAt(Instant.now())
                                        .build()));

        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .newStatus(TripStatus.IN_PROGRESS.name())
                        .previousStatus(TripStatus.CREATED.name())
                        .build();

        listener.onTripStatusChanged(event);

        verify(notificationRepository).saveAll(anyList());
        verify(userFollowRepository).findByFollowedId(tripOwnerId);
        verify(friendshipRepository).findByUserId(tripOwnerId);
    }

    @Test
    void onTripStatusChanged_ProtectedTrip_NotifiesFriendsOnly() {
        TripSettings settings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PROTECTED)
                        .build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(tripOwnerId)
                        .name("Camino")
                        .tripSettings(settings)
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(tripOwnerId))
                .thenReturn(
                        Optional.of(
                                User.builder().id(tripOwnerId).username("walker").build()));

        UUID friendId = UUID.randomUUID();
        when(friendshipRepository.findByUserId(tripOwnerId))
                .thenReturn(
                        List.of(
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(tripOwnerId)
                                        .friendId(friendId)
                                        .createdAt(Instant.now())
                                        .build()));

        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .newStatus(TripStatus.IN_PROGRESS.name())
                        .previousStatus(TripStatus.CREATED.name())
                        .build();

        listener.onTripStatusChanged(event);

        verify(notificationRepository).saveAll(anyList());
        verify(friendshipRepository).findByUserId(tripOwnerId);
        verify(userFollowRepository, never()).findByFollowedId(any());
    }

    @Test
    void onTripStatusChanged_PrivateTrip_DoesNotNotify() {
        TripSettings settings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PRIVATE)
                        .build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(tripOwnerId)
                        .name("Camino")
                        .tripSettings(settings)
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .newStatus(TripStatus.IN_PROGRESS.name())
                        .previousStatus(TripStatus.CREATED.name())
                        .build();

        listener.onTripStatusChanged(event);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    void onTripStatusChanged_Paused_DoesNotNotify() {
        TripStatusChangedEvent event =
                TripStatusChangedEvent.builder()
                        .tripId(tripId)
                        .newStatus(TripStatus.PAUSED.name())
                        .previousStatus(TripStatus.IN_PROGRESS.name())
                        .build();

        listener.onTripStatusChanged(event);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());
    }

    // ==================== TRIP UPDATED TESTS ====================

    @Test
    void onTripUpdated_PublicTrip_NotifiesFollowersAndFriends() {
        TripSettings settings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PUBLIC)
                        .build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(tripOwnerId)
                        .name("Camino")
                        .tripSettings(settings)
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(tripOwnerId))
                .thenReturn(
                        Optional.of(
                                User.builder().id(tripOwnerId).username("walker").build()));

        UUID followerId = UUID.randomUUID();
        when(userFollowRepository.findByFollowedId(tripOwnerId))
                .thenReturn(
                        List.of(
                                UserFollow.builder()
                                        .id(UUID.randomUUID())
                                        .followerId(followerId)
                                        .followedId(tripOwnerId)
                                        .createdAt(Instant.now())
                                        .build()));
        when(friendshipRepository.findByUserId(tripOwnerId)).thenReturn(List.of());

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .message("Having a great time!")
                        .updateType(UpdateType.REGULAR)
                        .timestamp(Instant.now())
                        .build();

        listener.onTripUpdated(event);

        verify(notificationRepository).saveAll(anyList());
        verify(userFollowRepository).findByFollowedId(tripOwnerId);
    }

    @Test
    void onTripUpdated_ProtectedTrip_NotifiesFriendsOnly() {
        TripSettings settings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PROTECTED)
                        .build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(tripOwnerId)
                        .name("Camino")
                        .tripSettings(settings)
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(tripOwnerId))
                .thenReturn(
                        Optional.of(
                                User.builder().id(tripOwnerId).username("walker").build()));

        UUID friendId = UUID.randomUUID();
        when(friendshipRepository.findByUserId(tripOwnerId))
                .thenReturn(
                        List.of(
                                Friendship.builder()
                                        .id(UUID.randomUUID())
                                        .userId(tripOwnerId)
                                        .friendId(friendId)
                                        .createdAt(Instant.now())
                                        .build()));

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .message("Having a great time!")
                        .updateType(UpdateType.REGULAR)
                        .timestamp(Instant.now())
                        .build();

        listener.onTripUpdated(event);

        verify(notificationRepository).saveAll(anyList());
        verify(friendshipRepository).findByUserId(tripOwnerId);
        verify(userFollowRepository, never()).findByFollowedId(any());
    }

    @Test
    void onTripUpdated_PrivateTrip_DoesNotNotify() {
        TripSettings settings =
                TripSettings.builder()
                        .tripStatus(TripStatus.IN_PROGRESS)
                        .visibility(TripVisibility.PRIVATE)
                        .build();
        Trip trip =
                Trip.builder()
                        .id(tripId)
                        .userId(tripOwnerId)
                        .name("Camino")
                        .tripSettings(settings)
                        .build();
        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .message("Having a great time!")
                        .updateType(UpdateType.REGULAR)
                        .timestamp(Instant.now())
                        .build();

        listener.onTripUpdated(event);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    void onTripUpdated_NoMessage_DoesNotNotify() {
        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .message(null)
                        .updateType(UpdateType.REGULAR)
                        .timestamp(Instant.now())
                        .build();

        listener.onTripUpdated(event);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    void onTripUpdated_SystemUpdateType_DoesNotNotify() {
        TripUpdatedEvent event =
                TripUpdatedEvent.builder()
                        .tripUpdateId(UUID.randomUUID())
                        .tripId(tripId)
                        .message("Day started")
                        .updateType(UpdateType.DAY_START)
                        .timestamp(Instant.now())
                        .build();

        listener.onTripUpdated(event);

        verify(notificationRepository, never()).save(any());
        verify(notificationRepository, never()).saveAll(anyList());
    }
}


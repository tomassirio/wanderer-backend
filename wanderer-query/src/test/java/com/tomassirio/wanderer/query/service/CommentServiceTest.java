package com.tomassirio.wanderer.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomassirio.wanderer.commons.domain.Comment;
import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.domain.User;
import com.tomassirio.wanderer.commons.dto.CommentDTO;
import com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory;
import com.tomassirio.wanderer.query.repository.CommentRepository;
import com.tomassirio.wanderer.query.service.impl.CommentServiceImpl;
import com.tomassirio.wanderer.query.utils.TestEntityFactory;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;

    @InjectMocks private CommentServiceImpl commentService;

    @Test
    void getComment_whenCommentExists_shouldReturnCommentDTO() {
        // Given
        UUID commentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        User user = TestEntityFactory.createUser();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");
        Comment comment = TestEntityFactory.createComment(commentId, user, trip);

        when(commentRepository.findByIdWithUser(commentId)).thenReturn(Optional.of(comment));

        // When
        CommentDTO result = commentService.getComment(commentId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(commentId.toString());
        assertThat(result.userId()).isEqualTo(TestEntityFactory.USER_ID.toString());
        assertThat(result.username()).isEqualTo(TestEntityFactory.USERNAME);
        assertThat(result.tripId()).isEqualTo(tripId.toString());
        assertThat(result.message()).isEqualTo("Test comment");
        assertThat(result.parentCommentId()).isNull();
        assertThat(result.replies()).isEmpty();
        assertThat(result.timestamp()).isNotNull();

        verify(commentRepository).findByIdWithUser(commentId);
    }

    @Test
    void getComment_whenCommentDoesNotExist_shouldThrowEntityNotFoundException() {
        // Given
        UUID nonExistentCommentId = UUID.randomUUID();
        when(commentRepository.findByIdWithUser(nonExistentCommentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> commentService.getComment(nonExistentCommentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Comment not found");

        verify(commentRepository).findByIdWithUser(nonExistentCommentId);
    }

    @Test
    void getComment_shouldMapReactionsCorrectly() {
        // Given
        UUID commentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");
        Comment comment =
                TestEntityFactory.createComment(
                        commentId, BaseTestEntityFactory.createUser(), trip);
        comment.getReactions().setHeart(5);
        comment.getReactions().setSmiley(3);

        when(commentRepository.findByIdWithUser(commentId)).thenReturn(Optional.of(comment));

        // When
        CommentDTO result = commentService.getComment(commentId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.reactions()).isNotNull();
        assertThat(result.reactions().heart()).isEqualTo(5);
        assertThat(result.reactions().smiley()).isEqualTo(3);

        verify(commentRepository).findByIdWithUser(commentId);
    }

    @Test
    void getCommentsForTrip_whenCommentsExist_shouldReturnPageOfCommentDTOs() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");

        Comment comment1 =
                TestEntityFactory.createComment(
                        UUID.randomUUID(), BaseTestEntityFactory.createUser(), trip);
        Comment comment2 =
                TestEntityFactory.createComment(
                        UUID.randomUUID(), BaseTestEntityFactory.createUser(), trip);

        List<Comment> comments = List.of(comment1, comment2);
        when(commentRepository.findTopLevelCommentsByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(comments, pageable, comments.size()));

        // When
        Page<CommentDTO> result = commentService.getCommentsForTrip(tripId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).tripId()).isEqualTo(tripId.toString());
        assertThat(result.getContent().get(0).message()).isEqualTo("Test comment");
        assertThat(result.getContent().get(1).tripId()).isEqualTo(tripId.toString());

        verify(commentRepository).findTopLevelCommentsByTripId(tripId, pageable);
    }

    @Test
    void getCommentsForTrip_whenNoCommentsExist_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        when(commentRepository.findTopLevelCommentsByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        // When
        Page<CommentDTO> result = commentService.getCommentsForTrip(tripId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(commentRepository).findTopLevelCommentsByTripId(tripId, pageable);
    }

    @Test
    void getCommentsForTrip_shouldOnlyReturnTopLevelComments() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");

        Comment topLevelComment =
                TestEntityFactory.createComment(
                        UUID.randomUUID(), BaseTestEntityFactory.createUser(), trip);

        when(commentRepository.findTopLevelCommentsByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(List.of(topLevelComment), pageable, 1));

        // When
        Page<CommentDTO> result = commentService.getCommentsForTrip(tripId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().parentCommentId()).isNull();

        verify(commentRepository).findTopLevelCommentsByTripId(tripId, pageable);
    }

    @Test
    void getCommentsForTrip_withMultipleComments_shouldReturnAllComments() {
        // Given
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        UUID tripId = UUID.randomUUID();
        Trip trip = TestEntityFactory.createTrip(tripId, "Test Trip");

        Comment comment1 =
                TestEntityFactory.createComment(
                        UUID.randomUUID(), BaseTestEntityFactory.createUser(), trip);
        Comment comment2 =
                TestEntityFactory.createComment(
                        UUID.randomUUID(), BaseTestEntityFactory.createUser(), trip);
        Comment comment3 =
                TestEntityFactory.createComment(
                        UUID.randomUUID(), BaseTestEntityFactory.createUser(), trip);

        List<Comment> comments = List.of(comment1, comment2, comment3);
        when(commentRepository.findTopLevelCommentsByTripId(tripId, pageable))
                .thenReturn(new PageImpl<>(comments, pageable, comments.size()));

        // When
        Page<CommentDTO> result = commentService.getCommentsForTrip(tripId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(dto -> dto.tripId().equals(tripId.toString()));
        assertThat(result.getContent()).allMatch(dto -> dto.parentCommentId() == null);

        verify(commentRepository).findTopLevelCommentsByTripId(tripId, pageable);
    }
}

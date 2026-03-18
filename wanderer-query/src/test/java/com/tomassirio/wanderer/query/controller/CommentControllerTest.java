package com.tomassirio.wanderer.query.controller;

import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USERNAME;
import static com.tomassirio.wanderer.commons.utils.BaseTestEntityFactory.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.commons.dto.CommentDTO;
import com.tomassirio.wanderer.commons.dto.ReactionsDTO;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import com.tomassirio.wanderer.query.service.CommentService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private static final String COMMENTS_BASE_URL = "/api/1/comments";
    private static final String TRIP_COMMENTS_URL = "/api/1/trips/{tripId}/comments";

    private MockMvc mockMvc;

    @Mock private CommentService commentService;

    @InjectMocks private CommentController commentController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        commentController, new GlobalExceptionHandler());
    }

    @Test
    void getComment_whenCommentExists_shouldReturnComment() throws Exception {
        // Given
        UUID commentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        CommentDTO comment = createCommentDTO(commentId, tripId, "Great trip!", null);

        when(commentService.getComment(commentId)).thenReturn(comment);

        // When & Then
        mockMvc.perform(get(COMMENTS_BASE_URL + "/{id}", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.message").value("Great trip!"))
                .andExpect(jsonPath("$.parentCommentId").isEmpty())
                .andExpect(jsonPath("$.replies").isArray())
                .andExpect(jsonPath("$.replies.length()").value(0));
    }

    @Test
    void getComment_whenCommentDoesNotExist_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentCommentId = UUID.randomUUID();

        when(commentService.getComment(nonExistentCommentId))
                .thenThrow(new EntityNotFoundException("Comment not found"));

        // When & Then
        mockMvc.perform(get(COMMENTS_BASE_URL + "/{id}", nonExistentCommentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComment_whenCommentHasReplies_shouldReturnCommentWithReplies() throws Exception {
        // Given
        UUID commentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID replyId1 = UUID.randomUUID();
        UUID replyId2 = UUID.randomUUID();

        CommentDTO reply1 = createCommentDTO(replyId1, tripId, "Reply 1", commentId.toString());
        CommentDTO reply2 = createCommentDTO(replyId2, tripId, "Reply 2", commentId.toString());

        CommentDTO comment =
                new CommentDTO(
                        commentId.toString(),
                        USER_ID.toString(),
                        USERNAME,
                        tripId.toString(),
                        null,
                        "Parent comment",
                        createReactionsDTO(5, 2),
                        List.of(), // individualReactions
                        List.of(reply1, reply2),
                        Instant.now());

        when(commentService.getComment(commentId)).thenReturn(comment);

        // When & Then
        mockMvc.perform(get(COMMENTS_BASE_URL + "/{id}", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.message").value("Parent comment"))
                .andExpect(jsonPath("$.replies").isArray())
                .andExpect(jsonPath("$.replies.length()").value(2))
                .andExpect(jsonPath("$.replies[0].message").value("Reply 1"))
                .andExpect(jsonPath("$.replies[1].message").value("Reply 2"))
                .andExpect(jsonPath("$.replies[0].parentCommentId").value(commentId.toString()))
                .andExpect(jsonPath("$.replies[1].parentCommentId").value(commentId.toString()));
    }

    @Test
    void getComment_whenCommentIsReply_shouldReturnReply() throws Exception {
        // Given
        UUID commentId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        CommentDTO reply =
                createCommentDTO(commentId, tripId, "This is a reply", parentCommentId.toString());

        when(commentService.getComment(commentId)).thenReturn(reply);

        // When & Then
        mockMvc.perform(get(COMMENTS_BASE_URL + "/{id}", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.message").value("This is a reply"))
                .andExpect(jsonPath("$.parentCommentId").value(parentCommentId.toString()));
    }

    @Test
    void getCommentsForTrip_whenCommentsExist_shouldReturnComments() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID commentId1 = UUID.randomUUID();
        UUID commentId2 = UUID.randomUUID();

        CommentDTO comment1 = createCommentDTO(commentId1, tripId, "First comment", null);
        CommentDTO comment2 = createCommentDTO(commentId2, tripId, "Second comment", null);

        when(commentService.getCommentsForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment1, comment2)));

        // When & Then
        mockMvc.perform(get(TRIP_COMMENTS_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(commentId1.toString()))
                .andExpect(jsonPath("$.content[0].message").value("First comment"))
                .andExpect(jsonPath("$.content[0].tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.content[1].id").value(commentId2.toString()))
                .andExpect(jsonPath("$.content[1].message").value("Second comment"))
                .andExpect(jsonPath("$.content[1].tripId").value(tripId.toString()));
    }

    @Test
    void getCommentsForTrip_whenNoCommentsExist_shouldReturnEmptyPage() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        when(commentService.getCommentsForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // When & Then
        mockMvc.perform(get(TRIP_COMMENTS_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getCommentsForTrip_whenTripDoesNotExist_shouldReturnNotFound() throws Exception {
        // Given
        UUID nonExistentTripId = UUID.randomUUID();

        when(commentService.getCommentsForTrip(eq(nonExistentTripId), any(Pageable.class)))
                .thenThrow(new EntityNotFoundException("Trip not found"));

        // When & Then
        mockMvc.perform(get(TRIP_COMMENTS_URL, nonExistentTripId)).andExpect(status().isNotFound());
    }

    @Test
    void getCommentsForTrip_whenCommentsHaveReplies_shouldReturnCommentsWithReplies()
            throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID replyId1 = UUID.randomUUID();
        UUID replyId2 = UUID.randomUUID();

        CommentDTO reply1 = createCommentDTO(replyId1, tripId, "Reply 1", commentId.toString());
        CommentDTO reply2 = createCommentDTO(replyId2, tripId, "Reply 2", commentId.toString());

        CommentDTO commentWithReplies =
                new CommentDTO(
                        commentId.toString(),
                        USER_ID.toString(),
                        USERNAME,
                        tripId.toString(),
                        null,
                        "Parent comment",
                        createReactionsDTO(3, 1),
                        List.of(), // individualReactions
                        List.of(reply1, reply2),
                        Instant.now());

        when(commentService.getCommentsForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(commentWithReplies)));

        // When & Then
        mockMvc.perform(get(TRIP_COMMENTS_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(commentId.toString()))
                .andExpect(jsonPath("$.content[0].message").value("Parent comment"))
                .andExpect(jsonPath("$.content[0].replies").isArray())
                .andExpect(jsonPath("$.content[0].replies.length()").value(2))
                .andExpect(jsonPath("$.content[0].replies[0].message").value("Reply 1"))
                .andExpect(jsonPath("$.content[0].replies[1].message").value("Reply 2"));
    }

    @Test
    void getCommentsForTrip_withMultipleTopLevelComments_shouldReturnAllComments()
            throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        List<CommentDTO> comments =
                List.of(
                        createCommentDTO(UUID.randomUUID(), tripId, "Comment 1", null),
                        createCommentDTO(UUID.randomUUID(), tripId, "Comment 2", null),
                        createCommentDTO(UUID.randomUUID(), tripId, "Comment 3", null),
                        createCommentDTO(UUID.randomUUID(), tripId, "Comment 4", null));

        when(commentService.getCommentsForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(comments));

        // When & Then
        mockMvc.perform(get(TRIP_COMMENTS_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].message").value("Comment 1"))
                .andExpect(jsonPath("$.content[1].message").value("Comment 2"))
                .andExpect(jsonPath("$.content[2].message").value("Comment 3"))
                .andExpect(jsonPath("$.content[3].message").value("Comment 4"));
    }

    @Test
    void getCommentsForTrip_whenCommentsHaveReactions_shouldReturnReactions() throws Exception {
        // Given
        UUID tripId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        CommentDTO commentWithReactions =
                new CommentDTO(
                        commentId.toString(),
                        USER_ID.toString(),
                        USERNAME,
                        tripId.toString(),
                        null,
                        "Comment with reactions",
                        createReactionsDTO(10, 5),
                        List.of(), // individualReactions
                        List.of(), // replies
                        Instant.now());

        when(commentService.getCommentsForTrip(eq(tripId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(commentWithReactions)));

        // When & Then
        mockMvc.perform(get(TRIP_COMMENTS_URL, tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reactions.heart").value(10))
                .andExpect(jsonPath("$.content[0].reactions.smiley").value(5));
    }

    private CommentDTO createCommentDTO(
            UUID commentId, UUID tripId, String message, String parentCommentId) {
        return new CommentDTO(
                commentId.toString(),
                USER_ID.toString(),
                USERNAME,
                tripId.toString(),
                parentCommentId,
                message,
                createReactionsDTO(0, 0),
                List.of(), // individualReactions
                List.of(), // replies
                Instant.now());
    }

    private ReactionsDTO createReactionsDTO(int heart, int smiley) {
        return new ReactionsDTO(heart, smiley, 0, 0, 0);
    }
}

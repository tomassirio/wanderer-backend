package com.tomassirio.wanderer.query.service.impl;

import com.tomassirio.wanderer.commons.dto.CommentDTO;
import com.tomassirio.wanderer.commons.mapper.CommentMapper;
import com.tomassirio.wanderer.query.repository.CommentRepository;
import com.tomassirio.wanderer.query.service.CommentService;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper = CommentMapper.INSTANCE;

    @Override
    public CommentDTO getComment(UUID commentId) {
        return commentRepository
                .findByIdWithUser(commentId)
                .map(commentMapper::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
    }

    @Override
    public Page<CommentDTO> getCommentsForTrip(UUID tripId, Pageable pageable) {
        return commentRepository
                .findTopLevelCommentsByTripId(tripId, pageable)
                .map(commentMapper::toDTO);
    }
}

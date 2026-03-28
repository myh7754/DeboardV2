package org.example.deboardv2.comment.repository;

import org.example.deboardv2.comment.dto.CommentDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentsCustomRepository {
    Page<CommentDetailResponse> findAll(Long postId, Pageable pageable);
    Page<CommentDetailResponse> findReplies(Long postId, Pageable pageable);

}

package org.example.deboardv2.comment.service;

import org.example.deboardv2.comment.dto.CommentDetailResponse;
import org.example.deboardv2.comment.dto.CommentCreateRequest;
import org.example.deboardv2.comment.entity.Comments;
import org.springframework.data.domain.Page;

public interface CommentsService {
    public Comments getCommentsById(Long id);
    public Page<CommentDetailResponse> readComments(Long postId, int size, int page);
    public Page<CommentDetailResponse> replies(Long postId, int size, int page);
    public void createComments(CommentCreateRequest request);
    public void updateComments(CommentCreateRequest request, Long commentId);
    public void deleteComments(Long commentsId);
}

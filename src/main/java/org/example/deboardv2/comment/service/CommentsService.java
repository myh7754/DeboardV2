package org.example.deboardv2.comment.service;

import org.example.deboardv2.comment.dto.CommentsDetail;
import org.example.deboardv2.comment.dto.CommentsRequest;
import org.example.deboardv2.comment.entity.Comments;
import org.example.deboardv2.post.dto.PostDetails;
import org.springframework.data.domain.Page;

public interface CommentsService {
    public Comments getCommentsById(Long id);
    public Page<CommentsDetail> readComments(Long postId, int size, int page);
    public Page<CommentsDetail> replies(Long postId, int size, int page);
    public void createComments(CommentsRequest request);
    public void updateComments(CommentsRequest request, Long commentId);
    public void deleteComments(Long commentsId);
}

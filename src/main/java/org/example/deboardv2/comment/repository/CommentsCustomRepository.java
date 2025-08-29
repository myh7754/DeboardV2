package org.example.deboardv2.comment.repository;

import org.example.deboardv2.comment.dto.CommentsDetail;
import org.example.deboardv2.post.dto.PostDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentsCustomRepository {
    Page<CommentsDetail> findAll(Long postId, Pageable pageable);
    Page<CommentsDetail> findReplies(Long postId, Pageable pageable);

}

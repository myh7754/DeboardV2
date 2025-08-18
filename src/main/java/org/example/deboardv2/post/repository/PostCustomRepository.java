package org.example.deboardv2.post.repository;

import org.example.deboardv2.post.dto.PostDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostCustomRepository {
    Page<PostDetails> findAll(Pageable pageable);
    PostDetails getPostDetails(Long postId);

}

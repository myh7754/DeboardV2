package org.example.deboardv2.post.repository;

import org.example.deboardv2.post.dto.PostDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostCustomRepository {
    Page<PostDetailResponse> findAll(Pageable pageable);
    PostDetailResponse getPostDetails(Long postId);
    Page<PostDetailResponse> searchPost(Pageable pageable, String searchType,String keyword);
    Page<PostDetailResponse> findLikesPosts(Pageable pageable);
    Page<PostDetailResponse> searchLikePosts( Pageable pageable, String searchType, String keyword);

}

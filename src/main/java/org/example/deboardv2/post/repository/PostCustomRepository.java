package org.example.deboardv2.post.repository;

import org.example.deboardv2.post.dto.PostDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostCustomRepository {
    Page<PostDetailResponse> findAll(Pageable pageable);
    PostDetailResponse getPostDetails(Long postId);
    Page<PostDetailResponse> searchPost(Pageable pageable, String searchType,String keyword);
    Page<PostDetailResponse> findLikesPosts(Pageable pageable);
    Page<PostDetailResponse> searchLikePosts( Pageable pageable, String searchType, String keyword);
    long countPublic();
    List<PostDetailResponse> getPublicList(Pageable pageable);

}

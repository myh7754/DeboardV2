package org.example.deboardv2.post.service;

import org.example.deboardv2.post.dto.PostCreateRequest;
import org.example.deboardv2.post.dto.PostDetailResponse;
import org.example.deboardv2.post.dto.PostUpdateRequest;
import org.example.deboardv2.post.entity.Post;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PostService {
    public PostDetailResponse save(PostCreateRequest post);
    public void saveBatch(List<Post> posts);
    public Post getPostById(Long postId);
    public Post getPostReferenceById(Long postId);
    public PostDetailResponse getPostDtoById(Long postId);
    public Page<PostDetailResponse> readAll(int size, int page);
    public Page<PostDetailResponse> readLikesPosts(int size, int page);
    public void delete(Long post);
    public void update(PostUpdateRequest post, Long postId);
}


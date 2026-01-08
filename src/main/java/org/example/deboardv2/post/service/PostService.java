package org.example.deboardv2.post.service;

import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.entity.Post;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PostService {
    public PostDetails save(PostCreateDto post);
    public void saveBatch(List<Post> posts);
    public Post getPostById(Long postId);
    public Post getPostReferenceById(Long postId);
    public PostDetails getPostDtoById(Long postId);
    public Page<PostDetails> readAll(int size, int page);
    public Page<PostDetails> readLikesPosts(int size, int page);
    public void delete(Long post);
    public void update(PostUpdateDto post, Long postId);
}


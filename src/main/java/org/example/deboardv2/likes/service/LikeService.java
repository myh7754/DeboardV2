package org.example.deboardv2.likes.service;

import org.example.deboardv2.post.entity.Post;

public interface LikeService {
    public void toggleLike(Long postId);
    public void toggleLike(Long postId, Long userId);
    public int getLikeCount(Long postId);
    public boolean getLikeStatus(Long postId);
}

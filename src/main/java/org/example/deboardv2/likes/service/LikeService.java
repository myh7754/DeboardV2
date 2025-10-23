package org.example.deboardv2.likes.service;

import org.example.deboardv2.post.entity.Post;

public interface LikeService {
    public void toggleLike(Long postId);
    public int getLikeCount(Long postId);
    public boolean getLikeStatus(Long postId);
    public void toggleLikeRecord(Long postId);
    public void updateLikeCount(Long postId);

}

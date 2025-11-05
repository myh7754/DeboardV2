package org.example.deboardv2.likes.service;

import org.example.deboardv2.user.entity.User;

public interface LikeService {
    public void toggleLike(Long postId);
    public int getLikeCount(Long postId);
    public boolean getLikeStatus(Long postId);
    public Boolean toggleLikeRecord(Long postId,User user);
    public void updateLikeCount(Long postId, boolean likeStatus);

}

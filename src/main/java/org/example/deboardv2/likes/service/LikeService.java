package org.example.deboardv2.likes.service;

import org.example.deboardv2.user.entity.User;

public interface LikeService {
    public int getLikeCount(Long postId);
    public boolean getLikeStatus(Long postId);
    public Boolean toggleLikeRecord(Long postId);
    public void updateLikeCount(Long postId, boolean likeStatus);

}

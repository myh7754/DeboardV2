package org.example.deboardv2.likes.service;

import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.user.entity.User;

public interface LikeService {
    public boolean getLikeStatus(Long postId);
    public void toggleLike(Long postId);


}

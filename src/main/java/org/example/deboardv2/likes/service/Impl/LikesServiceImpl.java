package org.example.deboardv2.likes.service.Impl;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikesServiceImpl implements LikeService {
    private final PostService postService;
    private final LikesRepository likesRepository;
    private final UserService userService;
    private final PostRepository postRepository;

    @Override
    public int getLikeCount(Long postId) {
        return postService.getPostById(postId).getLikeCount();
    }

    @Override
    public boolean getLikeStatus(Long postId) {
        return userService.getCurrentUserIdifExists()
                .map(userId -> likesRepository.existsByPostIdAndUserId(postId, userId))
                .orElse(false);
    }

    @Override
    @Transactional
    public Boolean toggleLikeRecord(Long postId) {
        User user = userService.getCurrentUser();
        Optional<Likes> likes = likesRepository.findByPostIdAndUserId(postId, user.getId());
        if (likes.isPresent()) {
            likesRepository.delete(likes.get());
            return false;
        } else {
            Likes entity = Likes.toEntity(user,postService.getPostById(postId));
            likesRepository.save(entity);
            return true;
        }

    }

    @Override
    @Transactional
    public void updateLikeCount(Long postId, boolean likeStatus) {
        if (likeStatus) {
            postRepository.increaseLikeCount(postId);
        } else {
            postRepository.decreaseLikeCount(postId);
        }
    }
}

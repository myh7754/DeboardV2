package org.example.deboardv2.likes.service.Impl;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikesServiceImpl implements LikeService {
    private final PostService postService;
    private final LikesRepository likesRepository;
    private final UserService userService;

    @Override
    public void toggleLike(Long postId) {
        User user = userService.getCurrentUser();
        Optional<Likes> likes = likesRepository.findByPostIdAndUserId(postId, user.getId());

        if (likes.isPresent()) {
            likesRepository.delete(likes.get());
        } else {
            Likes entity = Likes.toEntity(user, postService.getPostById(postId));
            likesRepository.save(entity);
        }

        // 같은 트랜잭션 내에서 카운트 업데이트
        int count = likesRepository.countByPostId(postId);
        Post postById = postService.getPostById(postId);
        postById.setLikeCount(count);
    }

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
    public void toggleLikeRecord(Long postId) {
        User user = userService.getCurrentUser();
        Optional<Likes> likes = likesRepository.findByPostIdAndUserId(postId, user.getId());
        if (likes.isPresent()) {
            likesRepository.delete(likes.get());
        } else {
            Likes entity = Likes.toEntity(user,postService.getPostById(postId));
            likesRepository.save(entity);
        }
    }

    @Override
    @Transactional
    public void updateLikeCount(Long postId) {
        int count = likesRepository.countByPostId(postId);
        Post postById = postService.getPostById(postId);
        postById.setLikeCount(count);
    }


}

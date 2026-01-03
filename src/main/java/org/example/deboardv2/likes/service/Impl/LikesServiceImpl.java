package org.example.deboardv2.likes.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LikesServiceImpl implements LikeService {
    private final PostService postService;
    private final LikesRepository likesRepository;
    private final UserService userService;
    private final PostRepository postRepository;
    private final RedisService redisService;
    private static final String LIKES_COUNT_KEY = "post:likes:count:"; // 총 개수
    private static final String CHANGED_KEY = "post:changed:ids";      // 변경된 게시물 목록


    @Override
    @Transactional(readOnly = true)
    public boolean getLikeStatus(Long postId) {
        return userService.getCurrentUserIdifExists()
                .map(userId -> likesRepository.existsByPostIdAndUserId(postId, userId))
                .orElse(false);
    }

    // 1. 원자적 업데이트
    @Override
    @Transactional
//    @Retryable(
//            value = {ObjectOptimisticLockingFailureException.class},
//            maxAttempts = 3,
//            backoff = @Backoff(delay = 100) // 재시도 간격 100ms
//    )
    public void toggleLike(Long postId) {
        Long userId = userService.getCurrentUserId();
        try {
            Optional<Likes> like = likesRepository.findByPostIdAndUserId(postId, userId);
            if (like.isPresent()) {
                postRepository.decreaseLikeCount(postId);
                likesRepository.delete(like.get());
            } else {
                Post post = postService.getPostReferenceById(postId);
                User user = userService.getUserReferenceById(userId);
                postRepository.increaseLikeCount(postId);
                likesRepository.save(Likes.toEntity(user, post));
            }
        } catch (DataIntegrityViolationException e) {
            log.error("중복 좋아요 요청 차단: userId = {}, postId = {}", userId, postId);

        }
    }
}

package org.example.deboardv2.likes.repository;

import jakarta.persistence.LockModeType;
import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Likes> findByPostIdAndUserId(Long postId, Long userId);
    long countByPost(Post post);
    // 좋아요한 게시글 목록
    List<Likes> findByPost(Post post);

    int countByPostId(Long postId);

    // 좋아요 존재 여부 확인
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostAndUser(Post post, User user);
    boolean existsByPostIdAndUser(Long postId, User user);

    void deleteByPostAndUser(Post post, User user);
    void deleteByPostIdAndUserId(Long postId, Long userId);
}

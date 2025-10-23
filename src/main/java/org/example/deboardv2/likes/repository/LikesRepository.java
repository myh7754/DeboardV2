package org.example.deboardv2.likes.repository;

import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
    Optional<Likes> findByPostIdAndUserId(Long postId, Long userId);
    long countByPost(Post post);
    // 좋아요한 게시글 목록
    List<Likes> findByPost(Post post);
    // 좋아요 존재 여부 확인
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    int countByPostId(Long postId);
}

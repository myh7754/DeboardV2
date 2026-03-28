package org.example.deboardv2.likes.repository;

import jakarta.persistence.LockModeType;
import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
    Optional<Likes> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    long countByPost(Post post);

    int countByPostId(Long postId);

    List<Likes> findByPost(Post post);
}

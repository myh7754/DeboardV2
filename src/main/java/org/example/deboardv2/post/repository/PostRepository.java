package org.example.deboardv2.post.repository;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.rss.domain.Feed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface PostRepository extends JpaRepository<Post,Long> {
    Optional<Post> findById(Long id);
    Page<Post> findAll(Pageable pageable);
    Boolean existsByIdAndAuthorId(Long postId, Long authorId);
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    void increaseLikeCount(@Param("postId") Long postId);
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    void decreaseLikeCount(@Param("postId") Long postId);

    @Query("SELECT p.link FROM Post p WHERE p.feed = :feed AND p.link IN :links")
    Set<String> findExistingLinksByFeed(@Param("feed") Feed feed, @Param("links") List<String> links);

    // 이 메서드를 호출하는 순간 Post 행에 DB 쓰기 잠금(P-Lock)을 강제로 겁니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

    void deleteByFeed(Feed feed);

}

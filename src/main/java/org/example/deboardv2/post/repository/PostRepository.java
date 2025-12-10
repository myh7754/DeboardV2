package org.example.deboardv2.post.repository;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;


public interface PostRepository extends JpaRepository<Post,Long> {
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Post> findById(Long id);
    Page<Post> findAll(Pageable pageable);
    @Modifying
    @Query("DELETE FROM Post p where p.id = :postId AND p.author.id = :authorId")
    int deleteByIdAndAuthorId(Long postId, Long authorId); // 권한 체크 후 삭제
    @Modifying
    @Query("UPDATE Post p " +
            "set p.title = :title, p.content = :content " +
            "WHERE p.id = :postId AND p.author.id = :authorId")
    int updateByIdAndAuthorId(Long postId, String title, String content, Long authorId);
    Boolean existsByIdAndAuthorId(Long postId, Long authorId);
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    void increaseLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    void decreaseLikeCount(@Param("postId") Long postId);

    boolean existsByLink(String link);
    boolean existsByLinkAndUserFeed(String link, UserFeed userFeed);

    @Query("SELECT p.link FROM Post p WHERE p.feed = :feed AND p.link IN :links")
    Set<String> findExistingLinksByFeed(@Param("feed") Feed feed, @Param("links") Set<String> links);
    @Query("SELECT p.link FROM Post p WHERE p.userFeed = :userFeed AND p.link IN :links")
    Set<String> findExistingLinksByUserFeed(@Param("userFeed") UserFeed userFeed, @Param("links") Set<String> links);

    // 비관적락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :postId")
    Optional<Post> findByIdForUpdate(Long postId);
}

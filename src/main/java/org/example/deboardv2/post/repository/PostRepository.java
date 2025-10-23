package org.example.deboardv2.post.repository;

import jakarta.persistence.LockModeType;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


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
}

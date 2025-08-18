package org.example.deboardv2.post.repository;

import org.example.deboardv2.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PostRepository extends JpaRepository<Post,Long> {
    Page<Post> findAll(Pageable pageable);

}

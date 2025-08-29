package org.example.deboardv2.comment.repository;

import org.example.deboardv2.comment.entity.Comments;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentsRepository extends JpaRepository<Comments, Long> {
}

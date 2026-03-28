package org.example.deboardv2.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deboardv2.comment.entity.Comments;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentsDetail {
    private Long commentsId;
    private String content;
    private LocalDateTime createdAt;
    private String author;
    private Long parentId;
    private Long repliesCount;
}

package org.example.deboardv2.comment.dto;

import lombok.Data;

@Data
public class CommentsRequest {
    public Long postId;
    public Long parentId;
    public String content;
}

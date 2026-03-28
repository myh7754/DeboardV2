package org.example.deboardv2.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentCreateRequest {
    @NotNull
    private Long postId;

    private Long parentId;

    @NotBlank
    private String content;
}

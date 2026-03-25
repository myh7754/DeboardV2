package org.example.deboardv2.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentsRequest {
    @NotNull
    public Long postId;

    public Long parentId;

    @NotBlank
    public String content;
}

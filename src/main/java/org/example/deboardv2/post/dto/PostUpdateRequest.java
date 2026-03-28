package org.example.deboardv2.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostUpdateRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;
}

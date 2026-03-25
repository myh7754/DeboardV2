package org.example.deboardv2.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostUpdateDto {
    @NotBlank
    @Size(max = 200)
    public String title;

    @NotBlank
    public String content;
}

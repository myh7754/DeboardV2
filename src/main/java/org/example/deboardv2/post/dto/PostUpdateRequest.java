package org.example.deboardv2.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostUpdateRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    // HTML 본문이라 태그가 붙는다. 이미지는 URL 로만 들어오므로 이 정도면 넉넉하다.
    @NotBlank
    @Size(max = 100_000)
    private String content;
}

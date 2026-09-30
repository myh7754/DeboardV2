package org.example.deboardv2.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deboardv2.post.entity.Post;

@Data
@NoArgsConstructor
public class PostCreateRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    // HTML 본문이라 태그가 붙는다. 이미지는 URL 로만 들어오므로 이 정도면 넉넉하다.
    @NotBlank
    @Size(max = 100_000)
    private String content;

    public PostCreateRequest(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public static PostCreateRequest from(Post post) {
        PostCreateRequest dto = new PostCreateRequest();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        return dto;
    }
}

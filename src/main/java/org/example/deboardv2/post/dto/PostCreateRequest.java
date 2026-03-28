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

    @NotBlank
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

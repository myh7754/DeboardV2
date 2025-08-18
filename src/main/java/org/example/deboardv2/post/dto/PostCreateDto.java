package org.example.deboardv2.post.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deboardv2.post.entity.Post;

@Data
@NoArgsConstructor
public class PostCreateDto {
    public String title;
    public String content;
    public String author;

    @QueryProjection
    public PostCreateDto(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public static PostCreateDto from(Post post) {
        PostCreateDto dto = new PostCreateDto();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.author = post.getAuthor().getNickname();
        return dto;
    }
}

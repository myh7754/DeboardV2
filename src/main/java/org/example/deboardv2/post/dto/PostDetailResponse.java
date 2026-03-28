package org.example.deboardv2.post.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deboardv2.post.entity.Post;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDetailResponse {
    Long id;
    String title;
    String content;
    String nickname;
    LocalDateTime createdAt;
    int likeCount;

    public static PostDetailResponse from(Post post) {
        PostDetailResponse postDetails = new PostDetailResponse();
        postDetails.setId(post.getId());
        postDetails.setTitle(post.getTitle());
        postDetails.setContent(post.getContent());
        postDetails.setCreatedAt(post.getCreatedAt());
        postDetails.setLikeCount(post.getLikeCount());
        return postDetails;
    }
}

package org.example.deboardv2.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.querydsl.core.annotations.QueryProjection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.deboardv2.post.entity.Post;

import java.time.LocalDateTime;

// 목록 응답에서는 content 가 채워지지 않으므로 JSON 에서 아예 생략한다
@JsonInclude(JsonInclude.Include.NON_NULL)
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

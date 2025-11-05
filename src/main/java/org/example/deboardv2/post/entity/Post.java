package org.example.deboardv2.post.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.deboardv2.comment.entity.Comments;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.dto.RssPost;
import org.example.deboardv2.system.baseentity.BaseEntity;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Post extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String content;

    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User author;
    //    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "likes_id")
//    private Likes likes;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "external_author_id")
    ExternalAuthor externalAuthor;

    @Column(unique = true)
    private String link;

    @Setter
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    public static Post from(PostCreateDto postDto, User user) {
        Post post = new Post();
        post.title = postDto.getTitle();
        post.content = postDto.getContent();
        post.author = user;
        return post;
    }

    public static Post fromRss(String title, String content, String link,
                               LocalDateTime createdAt, ExternalAuthor externalAuthor) {
        Post post = new Post();
        post.title = title;
        post.content = content;
        post.link = link;
        post.externalAuthor = externalAuthor;
        post.setCreatedAt(createdAt); // BaseEntity에 createdAt 필드 있을 경우 보호된 setter 사용
        return post;
    }

    public void update(PostUpdateDto postUpdateDto) {
        this.title = postUpdateDto.getTitle();
        this.content = postUpdateDto.getContent();
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
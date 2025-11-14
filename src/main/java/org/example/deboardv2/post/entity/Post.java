package org.example.deboardv2.post.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
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
    // 이미지 추가
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

    @ManyToOne(fetch = FetchType.LAZY)
    private Feed feed;        // 공용일 경우

    @ManyToOne(fetch = FetchType.LAZY)
    private UserFeed userFeed; // 개인일 경우

    private String link; // 게시글 원본 링크

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

    public static Post fromRss(String title, String content,String image, String link,
                               LocalDateTime createdAt, ExternalAuthor externalAuthor,Feed feed, UserFeed userFeed) {
        Post post = new Post();
        post.title = title;
        post.content = content;
        post.image = image;
        post.link = link;
        post.externalAuthor = externalAuthor;
        post.setCreatedAt(createdAt); // BaseEntity에 createdAt 필드 있을 경우 보호된 setter 사용
        post.feed = feed;
        post.userFeed = userFeed;
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
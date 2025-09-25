package org.example.deboardv2.post.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deboardv2.comment.entity.Comments;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.system.baseentity.BaseEntity;
import org.example.deboardv2.user.entity.User;

@Entity
@Getter
@NoArgsConstructor
public class Post extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String content;

    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User author;
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "likes_id")
//    private Likes likes;

    @Column(name = "like_count",nullable = false)
    private int likeCount = 0;

    public static Post from(PostCreateDto postDto, User user) {
        Post post = new Post();
        post.title = postDto.getTitle();
        post.content = postDto.getContent();
        post.author = user;
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
        if(this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void test() {
        this.id = 1000L;
        this.likeCount = 0;
    }

}

package org.example.deboardv2.likes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.user.entity.User;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "likes",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "post_id"})}) // 중복 방지
public class Likes {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "likes_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    public static Likes toEntity(User user, Post post) {
        Likes likes = new Likes();
        likes.post = post;
        likes.user = user;
        return likes;
    }
}

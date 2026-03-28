package org.example.deboardv2.comment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deboardv2.comment.dto.CommentCreateRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.system.baseentity.BaseEntity;
import org.example.deboardv2.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comments extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentsId;

    @Column(nullable = false)
    private String content;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comments parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comments> children = new ArrayList<>();


    public static Comments from(CommentCreateRequest dto, User user, Post post, Comments parent) {
        Comments comments = new Comments();
        comments.content = dto.getContent();
        comments.author = user;
        comments.post = post;
        comments.parent = parent;
        return comments;
    }

    public void updateContent(CommentCreateRequest dto) {
        this.content = dto.getContent();
    }
}

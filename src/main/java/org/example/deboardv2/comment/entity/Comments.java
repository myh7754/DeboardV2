package org.example.deboardv2.comment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.example.deboardv2.comment.dto.CommentsRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.system.baseentity.BaseEntity;
import org.example.deboardv2.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
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


    public static Comments from(CommentsRequest dto, User user, Post post, Comments parent) {
        Comments comments = new Comments();
        comments.content = dto.content;
        comments.author = user;
        comments.post = post;
        comments.parent = parent;
        return comments;
    }

    public void updateContent(CommentsRequest dto) {
        this.content = dto.content;
    }
}

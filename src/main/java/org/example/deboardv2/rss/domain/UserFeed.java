package org.example.deboardv2.rss.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeed { // 계정별 개인 블로그
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    private String feedUrl;   // https://velog.io/@user/rss
    private String siteName;  // Velog 아이디 or 블로그명

    @Builder.Default
    @OneToMany(mappedBy = "userFeed", cascade = CascadeType.REMOVE)
    private List<Post> posts = new ArrayList<>();
}
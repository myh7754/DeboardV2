package org.example.deboardv2.rss.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deboardv2.post.entity.Post;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feed { // 공통 블로그
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String siteName;
    @Column(unique = true)
    private String feedUrl; // rss url

    @Builder.Default
    @OneToMany(mappedBy = "feed", cascade = CascadeType.REMOVE)
    private List<Post> posts = new ArrayList<>();
}

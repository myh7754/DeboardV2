package org.example.deboardv2.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.deboardv2.rss.domain.Feed;

// 외부 게시글의 작성자 정보
@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ExternalAuthor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter
    private Long id;
    private String name;
//    private String sourceUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id")
    private Feed feed; // sourceUrl 대신 Feed 객체와 관계 형성

    public ExternalAuthor(String name, Feed feed) {
        this.name = name;
        this.feed = feed;
    }

    // 어떤 유저가 등록했는가
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "owner_user_id")
//    private User ownerUserId;

    public void update(String name, Feed feed) {
        this.name = name;
        this.feed = feed;
    }
}

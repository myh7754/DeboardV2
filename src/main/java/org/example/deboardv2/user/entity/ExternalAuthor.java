package org.example.deboardv2.user.entity;

import jakarta.persistence.*;

// 외부 게시글의 작성자 정보
@Entity
public class ExternalAuthor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String sourceUrl;

    private String siteType;
    // 어떤 유저가 등록했는가

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUserId;

    public void update(String name, String sourceUrl) {
        this.name = name;
        this.sourceUrl = sourceUrl;
    }
}

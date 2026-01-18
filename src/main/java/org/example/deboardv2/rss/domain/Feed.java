package org.example.deboardv2.rss.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class Feed {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String siteName;
    @Column(unique = true)
    private String feedUrl;
    @Enumerated(EnumType.STRING)
    private FeedType feedType;
    @Builder.Default
    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean isActive = true;

}

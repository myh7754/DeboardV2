package org.example.deboardv2.refactorrss.domain;

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

}

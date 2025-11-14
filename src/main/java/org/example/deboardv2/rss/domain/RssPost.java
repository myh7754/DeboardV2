package org.example.deboardv2.rss.domain;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RssPost {
    private String title;
    private String link;
    private String author;
    private String content;
    private String image;
    private LocalDateTime publishedAt;
    @Setter
    private Feed feed;
    @Setter
    private UserFeed userFeed;
}

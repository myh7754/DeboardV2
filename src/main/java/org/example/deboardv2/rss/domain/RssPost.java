package org.example.deboardv2.rss.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class RssPost {
    private String title;
    private String link;
    private String author;
    private String content;
    private String image;
    private LocalDateTime publishedAt;
    private Feed feed;

}

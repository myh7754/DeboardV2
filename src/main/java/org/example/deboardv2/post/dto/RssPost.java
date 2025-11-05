package org.example.deboardv2.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private LocalDateTime publishedAt;
}

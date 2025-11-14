package org.example.deboardv2.rss.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FeedDto {
    private Long id;
    private String siteName;
    private String feedUrl;
}

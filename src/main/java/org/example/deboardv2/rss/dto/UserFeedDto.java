package org.example.deboardv2.rss.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserFeedDto {
    private Long id;
    private String siteName;
    private String feedUrl;
}

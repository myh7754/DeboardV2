package org.example.deboardv2.post.dto;

import lombok.Data;

@Data
public class PostDetails {
    Long id;
    String title;
    String content;
    String author;
}

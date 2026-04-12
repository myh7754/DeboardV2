package org.example.deboardv2.post.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostPageCacheDto {
    private List<PostDetailResponse> content;
    private long totalCount;
}

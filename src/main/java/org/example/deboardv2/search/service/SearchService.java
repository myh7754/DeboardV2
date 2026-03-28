package org.example.deboardv2.search.service;

import org.example.deboardv2.post.dto.PostDetailResponse;
import org.springframework.data.domain.Page;

public interface SearchService {
    public Page<PostDetailResponse> search(String searchType,String search, int page, int size);
    public Page<PostDetailResponse> searchLikePosts(String searchType, String search, int page, int size);
}

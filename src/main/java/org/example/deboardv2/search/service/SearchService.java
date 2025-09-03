package org.example.deboardv2.search.service;

import org.example.deboardv2.post.dto.PostDetails;
import org.springframework.data.domain.Page;

public interface SearchService {
    public Page<PostDetails> search(String searchType,String search, int page, int size);
}

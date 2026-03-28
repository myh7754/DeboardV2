package org.example.deboardv2.search.service.Impl;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.post.dto.PostDetailResponse;
import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.search.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final PostCustomRepository postCustomRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> search(String searchType, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PostDetailResponse> postDetails = postCustomRepository.searchPost(pageable, searchType, search);
        return postDetails;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> searchLikePosts(String searchType, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PostDetailResponse> postDetails = postCustomRepository.searchLikePosts(pageable, searchType, search);
        return postDetails;
    }


}

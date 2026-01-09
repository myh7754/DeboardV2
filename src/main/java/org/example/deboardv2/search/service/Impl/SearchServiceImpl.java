package org.example.deboardv2.search.service.Impl;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.search.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final PostCustomRepository postCustomRepository;

    @Override
    public Page<PostDetails> search(String searchType, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PostDetails> postDetails = postCustomRepository.searchPost(pageable, searchType, search);
        return postDetails;
    }

    @Override
    public Page<PostDetails> seardhLikePosts(String searchType, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<PostDetails> postDetails = postCustomRepository.searchLikePosts(pageable, searchType, search);
        return postDetails;
    }


}

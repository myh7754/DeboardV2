package org.example.deboardv2.post.service.Impl;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostCreateRequest;
import org.example.deboardv2.post.dto.PostDetailResponse;
import org.example.deboardv2.post.dto.PostPageCacheDto;
import org.example.deboardv2.post.dto.PostUpdateRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.post.repository.PostJdbcRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.post.service.PostCacheService;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.redis.RedisKeyConstants;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.example.deboardv2.user.dto.TokenBody;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {
    private final UserService userService;
    private final AuthService authService;
    private final PostRepository postRepository;
    private final PostCustomRepository postCustomRepository;
    private final PostJdbcRepository postJdbcRepository;
    private final PostCacheService postCacheService;

    @Override
    @Transactional
    public PostDetailResponse save(PostCreateRequest post) {
        User user = userService.getCurrentUser();
        Post save = postRepository.save(Post.from(post, user));
        postCacheService.evict(RedisKeyConstants.POST_PUBLIC_PAGE + "0");
        postCacheService.evictPublicCount();
        return PostDetailResponse.from(save);
    }

    @Override
    @Transactional
    public void saveBatch(List<Post> posts) {
        postJdbcRepository.saveBatch(posts);
    }

    @Override
    @Transactional(readOnly = true)
    public Post getPostById(Long postId) {
        return postRepository.findById(postId).orElseThrow(
                ()-> new CustomException(ErrorCode.POST_NOT_FOUND)
        );
    }

    @Override
    public Post getPostReferenceById(Long postId) {
        return postRepository.getReferenceById(postId);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPostDtoById(Long postId) {
        return postCustomRepository.getPostDetails(postId);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> readAll(int size, int page) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAnonymous = (auth == null || "anonymousUser".equals(auth.getPrincipal()));

        if (isAnonymous) {
            if (page == 0 && size == 10) {
                return readAllCached(pageable);
            }
            return readAllAnonymous(pageable);
        }

        TokenBody tokenBody = (TokenBody) auth.getPrincipal();
        Long userId = tokenBody.getMemberId();

        long publicCount = postCacheService.getCachedPublicCount();
        List<Long> feedIds = postCacheService.getCachedPrivateFeedIds(userId);
        long privateCount = postCacheService.getCachedPrivateCount(userId, feedIds);

        return postCustomRepository.findAllLoggedIn(pageable, publicCount, feedIds, privateCount);
    }

    private Page<PostDetailResponse> readAllAnonymous(Pageable pageable) {
        List<PostDetailResponse> content = postCustomRepository.getPublicList(pageable);
        long total = postCacheService.getCachedPublicCount();
        return new PageImpl<>(content, pageable, total);
    }

    private Page<PostDetailResponse> readAllCached(Pageable pageable) {
        String cacheKey = RedisKeyConstants.POST_PUBLIC_PAGE + "0";

        PostPageCacheDto cached = postCacheService.get(cacheKey);

        if (cached != null) {
            if (postCacheService.isStale(cacheKey)) {
                postCacheService.refreshAsync(cacheKey, pageable);
            }
            return new PageImpl<>(cached.getContent(), pageable, cached.getTotalCount());
        }

        PostPageCacheDto refreshed = postCacheService.refreshSync(cacheKey, pageable);
        return new PageImpl<>(refreshed.getContent(), pageable, refreshed.getTotalCount());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> readLikesPosts(int size, int page) {
        Pageable pageable = PageRequest.of(page,size, Sort.by("id").descending());

        return postCustomRepository.findLikesPosts(pageable);
    }


    @Override
    @Transactional
    public void delete(Long postId) {
        authService.authCheck(postId, "POST");
        postRepository.deleteById(postId);
        postCacheService.evict(RedisKeyConstants.POST_PUBLIC_PAGE + "0");
        postCacheService.evictPublicCount();
    }

    @Override
    @Transactional
    public void update(PostUpdateRequest dto, Long postId) {
        authService.authCheck(postId, "POST");
        Post post = getPostById(postId);
        post.update(dto);
    }




}

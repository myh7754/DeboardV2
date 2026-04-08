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
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.redis.RedisKeyConstants;
import org.example.deboardv2.redis.service.RedisService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private final RedisService redisService;

    @Override
    @Transactional
    public PostDetailResponse save(PostCreateRequest post) {
        User user = userService.getCurrentUser();
        Post save = postRepository.save(Post.from(post, user));
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
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAnonymous = (auth == null || "anonymousUser".equals(auth.getPrincipal()));

        if (isAnonymous && page == 0 && size == 10) {
            return readAllCached(pageable);
        }

        return postCustomRepository.findAll(pageable);
    }

    private Page<PostDetailResponse> readAllCached(Pageable pageable) {
        String cacheKey = RedisKeyConstants.POST_PUBLIC_PAGE + "0";

        Object cached = redisService.getValue(cacheKey);
        if (cached instanceof PostPageCacheDto dto) {
            return new PageImpl<>(dto.getContent(), pageable, dto.getTotalCount());
        }

        Page<PostDetailResponse> result = postCustomRepository.findAll(pageable);
        redisService.setValueWithExpire(cacheKey,
                new PostPageCacheDto(result.getContent(), result.getTotalElements()),
                Duration.ofMinutes(5));
        return result;
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
    }

    @Override
    @Transactional
    public void update(PostUpdateRequest dto, Long postId) {
        authService.authCheck(postId, "POST");
        Post post = getPostById(postId);
        post.update(dto);
    }




}

package org.example.deboardv2.post.service.Impl;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.entity.Post;
//import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.post.service.PostService;
//import org.example.deboardv2.rss.service.RssService;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {
    public final UserService userService;
    public final PostRepository postRepository;
    public final PostCustomRepository postCustomRepository;
//    public final RssService rssService;
//
    @Override
    @Transactional
    public PostDetails save(PostCreateDto post) {
        User user = userService.getCurrentUser();
        Post save = postRepository.save(Post.from(post, user));
        return PostDetails.from(save);
    }

    @Override
    @Transactional
    public void saveBatch(List<Post> posts) {
        postRepository.saveAll(posts);
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
    public PostDetails getPostDtoById(Long postId) {
        return postCustomRepository.getPostDetails(postId);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<PostDetails> readAll(int size, int page) {
        Pageable pageable = PageRequest.of(page,size, Sort.by("id").descending());
        Page<PostDetails> postLists = postCustomRepository.findAll(pageable);
        return postLists;
    }

    @Override
    public Page<PostDetails> readLikesPosts(int size, int page) {
        Pageable pageable = PageRequest.of(page,size, Sort.by("id").descending());
        return postCustomRepository.findLikesPosts(pageable);
    }


    @Override
    @Transactional
    public void delete(Long postId) {
        int deletedCount = postRepository.deleteByIdAndAuthorId(postId, userService.getCurrentUserId());
        if (deletedCount == 0) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        postRepository.deleteById(postId);
    }

    @Override
    @Transactional
    public void update(PostUpdateDto dto, Long postId) {
        int updateCount = postRepository.updateByIdAndAuthorId(
                postId,
                dto.title,
                dto.content,
                userService.getCurrentUserId()
        );
        if (updateCount == 0) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        Post postReferenceById = getPostReferenceById(postId);
        postReferenceById.update(dto);
    }




}

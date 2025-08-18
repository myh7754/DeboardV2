package org.example.deboardv2.post.service.Impl;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.post.service.PostService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {
    public final UserService userService;
    public final PostRepository postRepository;
    public final PostCustomRepository  postCustomRepository;

    @Override
    @Transactional
    public void save(PostCreateDto post) {
        User user = userService.getUserByNickname(post.getAuthor());
        postRepository.save(Post.from(post, user));
    }

    @Override
    @Transactional(readOnly = true)
    public Post getPostById(Long postId) {
        return postRepository.findById(postId).orElseThrow(
                ()-> new CustomException(ErrorCode.POST_NOT_FOUND)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Post getPostReferenceById(Long postId) {
        return postRepository.getReferenceById(postId);
    }

    @Override
    public PostDetails getPostDtoById(Long postId) {
        return postCustomRepository.getPostDetails(postId);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<PostDetails> readAll(int size, int page) {
        Pageable pageable = PageRequest.of(page,size, Sort.by("id").descending());
        return postCustomRepository.findAll(pageable);
    }


    @Override
    @Transactional
    public void delete(Long postId) {
        postRepository.deleteById(postId);
    }

    @Override
    @Transactional
    public void update(PostUpdateDto postUpdateDto, Long postId) {
        Post postReferenceById = getPostReferenceById(postId);
        postReferenceById.update(postUpdateDto);
    }
}

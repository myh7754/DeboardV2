package org.example.deboardv2.comment.service.Impl;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.comment.dto.CommentsDetail;
import org.example.deboardv2.comment.dto.CommentsRequest;
import org.example.deboardv2.comment.entity.Comments;
import org.example.deboardv2.comment.repository.CommentsCustomRepository;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.comment.service.CommentsService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentsServiceImpl implements CommentsService {
    private final CommentsCustomRepository customRepository;
    private final CommentsRepository commentsRepository;
    private final PostRepository postRepository;
    private final UserService userService;
    private final AuthService authService;

    @Override
    @Transactional(readOnly = true)
    public Comments getCommentsById(Long id) {
        return commentsRepository.findById(id).orElseThrow(
                ()-> new CustomException(ErrorCode.COMMENT_NOT_FOUND)
        );
    }

    // 부모 댓글 페이징
    @Override
    public Page<CommentsDetail> readComments(Long postId, int size, int page) {
        Pageable pageable = PageRequest.of(page,size, Sort.by("id").descending());
        return customRepository.findAll(postId, pageable);
    }

    @Override
    public Page<CommentsDetail> replies(Long parentId, int size, int page) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("commentsId").ascending());
        return customRepository.findReplies(parentId, pageable);
    }

    // 특정 부모 댓글의 대댓글 페이징

    @Override
    @Transactional
    public void createComments(CommentsRequest request) {
        Post referenceById = postRepository.getReferenceById(request.postId);
        Comments parent = null;
        if (request.getParentId() != null) {
            parent = getCommentsById(request.getParentId());
        }
        Comments from = Comments.from(request, userService.getCurrentUser(), referenceById, parent);
        commentsRepository.save(from);

    }

    @Override
    @Transactional
    public void updateComments(CommentsRequest request, Long commentId) {
        authService.authCheck(commentId, "COMMENT");
        Comments commentsById = getCommentsById(commentId);
        commentsById.updateContent(request);
    }

    @Override
    @Transactional
    public void deleteComments(Long commentsId) {
        commentsRepository.deleteById(commentsId);
    }
}

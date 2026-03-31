package org.example.deboardv2.comment.service;

import org.example.deboardv2.comment.dto.CommentDetailResponse;
import org.example.deboardv2.comment.dto.CommentCreateRequest;
import org.example.deboardv2.comment.entity.Comments;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.post.dto.PostCreateRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentsIntegrationTest {

    @Autowired
    CommentsService commentsService;

    @Autowired
    CommentsRepository commentsRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    UserRepository userRepository;

    private User owner;
    private User otherUser;
    private Post post;

    @BeforeEach
    void setUp() {
        // owner 사용자 생성
        SignupRequest ownerRequest = new SignupRequest();
        ownerRequest.setNickname("owner_nick");
        ownerRequest.setEmail("owner@test.com");
        ownerRequest.setPassword("password123!");
        owner = userRepository.save(User.toEntity(ownerRequest));

        // otherUser 사용자 생성
        SignupRequest otherRequest = new SignupRequest();
        otherRequest.setNickname("other_nick");
        otherRequest.setEmail("other@test.com");
        otherRequest.setPassword("password123!");
        otherUser = userRepository.save(User.toEntity(otherRequest));

        // Post 생성
        PostCreateRequest postCreateDto = new PostCreateRequest();
        postCreateDto.setTitle("테스트 게시글");
        postCreateDto.setContent("테스트 내용");
        post = postRepository.save(Post.from(postCreateDto, owner));

        // SecurityContext를 owner로 설정
        setSecurityContext(owner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // SecurityContextHolder에 TokenBody를 설정하는 헬퍼 메서드
    private void setSecurityContext(User user) {
        TokenBody tokenBody = new TokenBody(user.getId(), user.getNickname(), Role.ROLE_MEMBER);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(tokenBody, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // CommentCreateRequest 생성 헬퍼
    private CommentCreateRequest buildRequest(Long postId, Long parentId, String content) {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(postId);
        request.setParentId(parentId);
        request.setContent(content);
        return request;
    }

    @Test
    @DisplayName("createComments() - parentId null이면 부모 댓글 생성 성공")
    void createComments_parentIdNull_부모댓글생성() {
        // given
        CommentCreateRequest request = buildRequest(post.getId(), null, "부모 댓글 내용");

        // when
        commentsService.createComments(request);

        // then
        List<Comments> all = commentsRepository.findAll();
        assertThat(all).hasSize(1);
        Comments saved = all.get(0);
        assertThat(saved.getContent()).isEqualTo("부모 댓글 내용");
        assertThat(saved.getParent()).isNull();
        assertThat(saved.getAuthor().getId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("createComments() - parentId 지정하면 대댓글 생성, parent 연관 확인")
    void createComments_parentId지정_대댓글생성() {
        // given - 부모 댓글 먼저 생성
        CommentCreateRequest parentRequest = buildRequest(post.getId(), null, "부모 댓글");
        commentsService.createComments(parentRequest);

        Comments parentComment = commentsRepository.findAll().get(0);

        CommentCreateRequest replyRequest = buildRequest(post.getId(), parentComment.getCommentsId(), "대댓글 내용");

        // when
        commentsService.createComments(replyRequest);

        // then
        List<Comments> all = commentsRepository.findAll();
        Comments reply = all.stream()
                .filter(c -> c.getParent() != null)
                .findFirst()
                .orElseThrow();
        assertThat(reply.getContent()).isEqualTo("대댓글 내용");
        assertThat(reply.getParent().getCommentsId()).isEqualTo(parentComment.getCommentsId());
    }

    @Test
    @DisplayName("createComments() - 존재하지 않는 parentId이면 COMMENT_NOT_FOUND 예외 발생")
    void createComments_존재하지않는parentId_COMMENT_NOT_FOUND() {
        // given
        CommentCreateRequest request = buildRequest(post.getId(), 99999L, "대댓글 내용");

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> commentsService.createComments(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("updateComments() - 본인 댓글이면 내용 수정 성공")
    void updateComments_본인댓글_수정성공() {
        // given
        CommentCreateRequest createRequest = buildRequest(post.getId(), null, "원본 내용");
        commentsService.createComments(createRequest);

        Comments saved = commentsRepository.findAll().get(0);
        Long commentId = saved.getCommentsId();

        CommentCreateRequest updateRequest = buildRequest(post.getId(), null, "수정된 내용");

        // when
        commentsService.updateComments(updateRequest, commentId);

        // then
        Comments updated = commentsRepository.findById(commentId).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("updateComments() - 타인 댓글이면 FORBIDDEN 예외 발생")
    void updateComments_타인댓글_FORBIDDEN() {
        // given - owner가 댓글 생성
        CommentCreateRequest createRequest = buildRequest(post.getId(), null, "owner 댓글");
        commentsService.createComments(createRequest);

        Comments saved = commentsRepository.findAll().get(0);
        Long commentId = saved.getCommentsId();

        // otherUser로 SecurityContext 전환
        setSecurityContext(otherUser);

        CommentCreateRequest updateRequest = buildRequest(post.getId(), null, "수정 시도");

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> commentsService.updateComments(updateRequest, commentId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("deleteComments() - 본인 댓글이면 삭제 성공")
    void deleteComments_본인댓글_삭제성공() {
        // given
        CommentCreateRequest createRequest = buildRequest(post.getId(), null, "삭제할 댓글");
        commentsService.createComments(createRequest);

        Comments saved = commentsRepository.findAll().get(0);
        Long commentId = saved.getCommentsId();

        // when
        commentsService.deleteComments(commentId);

        // then
        assertThat(commentsRepository.findById(commentId)).isEmpty();
    }

    @Test
    @DisplayName("deleteComments() - 타인 댓글이면 FORBIDDEN 예외 발생")
    void deleteComments_타인댓글_FORBIDDEN() {
        // given - owner가 댓글 생성
        CommentCreateRequest createRequest = buildRequest(post.getId(), null, "owner 댓글");
        commentsService.createComments(createRequest);

        Comments saved = commentsRepository.findAll().get(0);
        Long commentId = saved.getCommentsId();

        // otherUser로 SecurityContext 전환
        setSecurityContext(otherUser);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> commentsService.deleteComments(commentId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("readComments() - 부모 댓글만 조회되고 repliesCount 정확히 반환")
    void readComments_부모댓글만조회_repliesCount정확성() {
        // given - 부모 댓글 1개 생성
        CommentCreateRequest parentRequest = buildRequest(post.getId(), null, "부모 댓글");
        commentsService.createComments(parentRequest);

        // 생성된 부모 댓글 ID 조회
        Comments parentComment = commentsRepository.findAll().stream()
                .filter(c -> c.getParent() == null)
                .findFirst()
                .orElseThrow();

        // 대댓글 3개 생성
        for (int i = 1; i <= 3; i++) {
            CommentCreateRequest replyRequest = buildRequest(post.getId(), parentComment.getCommentsId(), "대댓글 " + i);
            commentsService.createComments(replyRequest);
        }

        // when
        Page<CommentDetailResponse> result = commentsService.readComments(post.getId(), 10, 0);

        // then - 부모 댓글 1개만 조회
        assertThat(result.getTotalElements()).isEqualTo(1);
        CommentDetailResponse detail = result.getContent().get(0);
        assertThat(detail.getCommentsId()).isEqualTo(parentComment.getCommentsId());
        assertThat(detail.getRepliesCount()).isEqualTo(3L);
        assertThat(detail.getParentId()).isNull();
    }

    @Test
    @DisplayName("replies() - 특정 부모의 대댓글 목록 3개 반환, 각 repliesCount=0")
    void replies_특정부모의대댓글목록_repliesCount_0() {
        // given - 부모 댓글 1개 생성
        CommentCreateRequest parentRequest = buildRequest(post.getId(), null, "부모 댓글");
        commentsService.createComments(parentRequest);

        Comments parentComment = commentsRepository.findAll().stream()
                .filter(c -> c.getParent() == null)
                .findFirst()
                .orElseThrow();

        // 대댓글 3개 생성
        for (int i = 1; i <= 3; i++) {
            CommentCreateRequest replyRequest = buildRequest(post.getId(), parentComment.getCommentsId(), "대댓글 " + i);
            commentsService.createComments(replyRequest);
        }

        // when
        Page<CommentDetailResponse> result = commentsService.replies(parentComment.getCommentsId(), 10, 0);

        // then - 대댓글 3개 반환
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);

        // 각 대댓글의 repliesCount = 0
        result.getContent().forEach(reply -> {
            assertThat(reply.getRepliesCount()).isEqualTo(0L);
            assertThat(reply.getParentId()).isEqualTo(parentComment.getCommentsId());
        });
    }
}

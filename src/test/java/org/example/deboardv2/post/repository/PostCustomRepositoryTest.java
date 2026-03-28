package org.example.deboardv2.post.repository;

import org.example.deboardv2.likes.entity.Likes;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedSubscription;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.repository.FeedRepository;
import org.example.deboardv2.rss.repository.FeedSubscriptionRepository;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.example.deboardv2.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostCustomRepositoryTest {

    @Autowired
    private PostCustomRepository postCustomRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedSubscriptionRepository feedSubscriptionRepository;

    @Autowired
    private ExternalAuthorRepository externalAuthorRepository;

    @Autowired
    private LikesRepository likesRepository;

    private static final Pageable PAGE = PageRequest.of(0, 100);

    // 테스트 데이터 참조 변수
    private User subscriberUser;
    private User nonSubscriberUser;
    private Feed publicFeed;
    private Feed privateFeed;
    private Post publicFeedPost;
    private Post privateSubscribedPost;
    private Post userAuthoredPost;
    private Post externalAuthorPost;

    @BeforeEach
    void setUp() {
        // 구독자 User 생성
        SignupRequest subscriberReq = new SignupRequest();
        subscriberReq.nickname = "subscriber_nick";
        subscriberReq.email = "subscriber@test.com";
        subscriberReq.password = "password123";
        subscriberUser = userRepository.save(User.toEntity(subscriberReq));

        // 비구독자 User 생성
        SignupRequest nonSubReq = new SignupRequest();
        nonSubReq.nickname = "nonsub_nick";
        nonSubReq.email = "nonsub@test.com";
        nonSubReq.password = "password123";
        nonSubscriberUser = userRepository.save(User.toEntity(nonSubReq));

        // PUBLIC Feed 생성
        publicFeed = feedRepository.save(Feed.builder()
                .siteName("공개 피드")
                .feedUrl("http://public.feed.test/rss")
                .feedType(FeedType.PUBLIC)
                .build());

        // PRIVATE Feed 생성
        privateFeed = feedRepository.save(Feed.builder()
                .siteName("비공개 피드")
                .feedUrl("http://private.feed.test/rss")
                .feedType(FeedType.PRIVATE)
                .build());

        // ExternalAuthor 생성 (PUBLIC Feed 소속)
        ExternalAuthor externalAuthor = externalAuthorRepository.save(
                new ExternalAuthor("외부작가닉네임", publicFeed)
        );

        // PUBLIC Feed 소속 외부 게시글 (externalAuthor, feed 있음)
        publicFeedPost = postRepository.save(Post.fromRss(
                "공개피드 제목입니다",
                "공개피드 내용입니다",
                null,
                "http://public.feed.test/post/1",
                LocalDateTime.now().minusHours(3),
                externalAuthor,
                publicFeed
        ));

        // PUBLIC Feed 소속 외부 게시글 — ExternalAuthor 검색용
        externalAuthorPost = postRepository.save(Post.fromRss(
                "외부작가 게시글 제목",
                "외부작가 게시글 내용",
                null,
                "http://public.feed.test/post/2",
                LocalDateTime.now().minusHours(2),
                externalAuthor,
                publicFeed
        ));

        // PRIVATE Feed 소속 외부 게시글
        ExternalAuthor privateExternalAuthor = externalAuthorRepository.save(
                new ExternalAuthor("비공개외부작가", privateFeed)
        );
        privateSubscribedPost = postRepository.save(Post.fromRss(
                "비공개피드 제목",
                "비공개피드 내용",
                null,
                "http://private.feed.test/post/1",
                LocalDateTime.now().minusHours(1),
                privateExternalAuthor,
                privateFeed
        ));

        // User 직접 작성 게시글 (author != null, feed == null)
        PostCreateDto postCreateDto = new PostCreateDto();
        postCreateDto.setTitle("유저 직접 작성 제목");
        postCreateDto.setContent("유저 직접 작성 내용");
        userAuthoredPost = postRepository.save(Post.from(postCreateDto, subscriberUser));

        // subscriberUser가 PRIVATE Feed 구독
        feedSubscriptionRepository.save(FeedSubscription.builder()
                .feed(privateFeed)
                .user(subscriberUser)
                .customName("내 비공개 피드")
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== findAll() 테스트 ==========

    @Test
    @DisplayName("findAll - 비인증 사용자는 PUBLIC 피드 게시글과 author(User) 있는 게시글만 조회된다")
    void findAll_비인증_PUBLIC피드와_유저작성글만_노출() {
        // given - SecurityContextHolder 비인증 상태 유지 (기본값: anonymousUser 없음)
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.findAll(PAGE);

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(publicFeedPost.getId());       // PUBLIC 피드 게시글 노출
        assertThat(postIds).contains(externalAuthorPost.getId());   // PUBLIC 피드 게시글 노출
        assertThat(postIds).contains(userAuthoredPost.getId());      // author(User) 있는 게시글 노출
        assertThat(postIds).doesNotContain(privateSubscribedPost.getId()); // PRIVATE 피드 게시글 미노출
    }

    @Test
    @DisplayName("findAll - 인증 + 구독 사용자는 PRIVATE 피드 게시글도 조회된다")
    void findAll_인증_구독자_PRIVATE피드_포함() {
        // given
        setAuthentication(subscriberUser);

        // when
        Page<PostDetails> result = postCustomRepository.findAll(PAGE);

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(privateSubscribedPost.getId()); // 구독 PRIVATE 피드 게시글 포함
        assertThat(postIds).contains(publicFeedPost.getId());
        assertThat(postIds).contains(userAuthoredPost.getId());
    }

    @Test
    @DisplayName("findAll - PRIVATE 피드는 비구독 인증 사용자에게 노출되지 않는다")
    void findAll_인증_비구독자_PRIVATE피드_미노출() {
        // given - 비구독자로 인증
        setAuthentication(nonSubscriberUser);

        // when
        Page<PostDetails> result = postCustomRepository.findAll(PAGE);

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).doesNotContain(privateSubscribedPost.getId()); // PRIVATE 피드 미노출
        assertThat(postIds).contains(publicFeedPost.getId());              // PUBLIC 피드는 노출
    }

    // ========== searchPost() 테스트 ==========

    @Test
    @DisplayName("searchPost - type=title, keyword가 제목에 포함된 게시글만 조회된다")
    void searchPost_type_title_keyword_매칭() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.searchPost(PAGE, "title", "공개피드");

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(publicFeedPost.getId());
        assertThat(postIds).doesNotContain(userAuthoredPost.getId()); // "유저 직접 작성 제목"은 불포함
    }

    @Test
    @DisplayName("searchPost - type=content, keyword가 내용에 포함된 게시글만 조회된다")
    void searchPost_type_content_keyword_매칭() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.searchPost(PAGE, "content", "공개피드 내용");

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(publicFeedPost.getId());
        assertThat(postIds).doesNotContain(externalAuthorPost.getId()); // "외부작가 게시글 내용"은 불포함
    }

    @Test
    @DisplayName("searchPost - type=author, User nickname과 ExternalAuthor name 모두 검색된다")
    void searchPost_type_author_유저닉네임_및_외부작가_검색() {
        // given
        SecurityContextHolder.clearContext();

        // when - User nickname 검색 (user authored post)
        Page<PostDetails> userNicknameResult = postCustomRepository.searchPost(PAGE, "author", "subscriber_nick");

        // then
        assertThat(userNicknameResult.getContent().stream()
                .map(PostDetails::getId)
                .toList())
                .contains(userAuthoredPost.getId());

        // when - ExternalAuthor name 검색
        Page<PostDetails> externalAuthorResult = postCustomRepository.searchPost(PAGE, "author", "외부작가닉네임");

        // then
        assertThat(externalAuthorResult.getContent().stream()
                .map(PostDetails::getId)
                .toList())
                .contains(publicFeedPost.getId())
                .contains(externalAuthorPost.getId());
    }

    @Test
    @DisplayName("searchPost - type=titleContent, 제목 OR 내용에 keyword가 포함된 게시글이 조회된다")
    void searchPost_type_titleContent_제목_OR_내용_검색() {
        // given
        SecurityContextHolder.clearContext();

        // when - "외부작가"는 externalAuthorPost의 제목에 포함됨
        Page<PostDetails> result = postCustomRepository.searchPost(PAGE, "titleContent", "외부작가");

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(externalAuthorPost.getId());
    }

    @Test
    @DisplayName("searchPost - keyword가 빈 문자열이면 가시성 필터만 적용하여 전체 조회된다")
    void searchPost_keyword_빈문자열_전체조회() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.searchPost(PAGE, "title", "");

        // then
        // 비인증이므로 PUBLIC + author있는 게시글만 조회
        assertThat(result.getTotalElements()).isGreaterThan(0);
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).doesNotContain(privateSubscribedPost.getId()); // PRIVATE 피드 미노출
        assertThat(postIds).contains(publicFeedPost.getId());
        assertThat(postIds).contains(userAuthoredPost.getId());
    }

    @Test
    @DisplayName("searchPost - keyword가 null이면 가시성 필터만 적용하여 전체 조회된다")
    void searchPost_keyword_null_전체조회() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.searchPost(PAGE, "title", null);

        // then
        assertThat(result.getTotalElements()).isGreaterThan(0);
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).doesNotContain(privateSubscribedPost.getId());
        assertThat(postIds).contains(publicFeedPost.getId());
    }

    // ========== findLikesPosts() 테스트 ==========

    @Test
    @DisplayName("findLikesPosts - 비인증 사용자는 빈 페이지를 반환한다")
    void findLikesPosts_비인증_빈페이지() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.findLikesPosts(PAGE);

        // then
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("findLikesPosts - 인증 사용자는 본인이 좋아요한 게시글만 반환한다")
    void findLikesPosts_인증_본인_좋아요_게시글만_반환() {
        // given
        likesRepository.save(Likes.toEntity(subscriberUser, publicFeedPost));
        likesRepository.save(Likes.toEntity(subscriberUser, userAuthoredPost));

        setAuthentication(subscriberUser);

        // when
        Page<PostDetails> result = postCustomRepository.findLikesPosts(PAGE);

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(publicFeedPost.getId());
        assertThat(postIds).contains(userAuthoredPost.getId());
        assertThat(postIds).doesNotContain(externalAuthorPost.getId()); // 좋아요하지 않은 게시글 미포함
    }

    @Test
    @DisplayName("findLikesPosts - 다른 사용자의 좋아요 게시글은 포함되지 않는다")
    void findLikesPosts_타사용자_좋아요_미포함() {
        // given - subscriberUser만 publicFeedPost 좋아요
        likesRepository.save(Likes.toEntity(subscriberUser, publicFeedPost));

        // nonSubscriberUser로 인증
        setAuthentication(nonSubscriberUser);

        // when
        Page<PostDetails> result = postCustomRepository.findLikesPosts(PAGE);

        // then
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ========== searchLikePosts() 테스트 ==========

    @Test
    @DisplayName("searchLikePosts - 비인증 사용자는 빈 페이지를 반환한다")
    void searchLikePosts_비인증_빈페이지() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Page<PostDetails> result = postCustomRepository.searchLikePosts(PAGE, "title", "공개");

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("searchLikePosts - 인증 사용자의 좋아요 게시글 중 keyword가 제목에 포함된 게시글만 반환된다")
    void searchLikePosts_인증_좋아요_게시글_keyword_검색() {
        // given - subscriberUser가 두 게시글 모두 좋아요
        likesRepository.save(Likes.toEntity(subscriberUser, publicFeedPost));    // "공개피드 제목입니다"
        likesRepository.save(Likes.toEntity(subscriberUser, externalAuthorPost)); // "외부작가 게시글 제목"

        setAuthentication(subscriberUser);

        // when - "공개피드" keyword로 제목 검색
        Page<PostDetails> result = postCustomRepository.searchLikePosts(PAGE, "title", "공개피드");

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(publicFeedPost.getId());
        assertThat(postIds).doesNotContain(externalAuthorPost.getId()); // "외부작가 게시글 제목"은 불포함
    }

    @Test
    @DisplayName("searchLikePosts - 좋아요한 게시글 중 내용으로도 검색이 가능하다")
    void searchLikePosts_인증_좋아요_게시글_내용_검색() {
        // given
        likesRepository.save(Likes.toEntity(subscriberUser, publicFeedPost));    // content: "공개피드 내용입니다"
        likesRepository.save(Likes.toEntity(subscriberUser, userAuthoredPost));  // content: "유저 직접 작성 내용"

        setAuthentication(subscriberUser);

        // when
        Page<PostDetails> result = postCustomRepository.searchLikePosts(PAGE, "content", "유저 직접");

        // then
        List<Long> postIds = result.getContent().stream()
                .map(PostDetails::getId)
                .toList();

        assertThat(postIds).contains(userAuthoredPost.getId());
        assertThat(postIds).doesNotContain(publicFeedPost.getId());
    }

    // ========== 헬퍼 메서드 ==========

    private void setAuthentication(User user) {
        TokenBody tokenBody = new TokenBody(user.getId(), user.getNickname(), user.getRole());
        Authentication auth = new UsernamePasswordAuthenticationToken(tokenBody, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

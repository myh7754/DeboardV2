package org.example.deboardv2.post.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.QBean;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.likes.entity.QLikes;
import org.example.deboardv2.post.dto.PostDetailResponse;
import org.example.deboardv2.post.entity.QPost;
import org.example.deboardv2.rss.domain.QFeed;
import org.example.deboardv2.rss.domain.QFeedSubscription;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.QExternalAuthor;
import org.example.deboardv2.user.entity.QUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
@Slf4j
public class PostCustomRepositoryImpl implements PostCustomRepository {
    private final JPAQueryFactory queryFactory;
    QPost qPost = QPost.post;
    QUser qUser = QUser.user;
    QExternalAuthor qExternalAuthor = QExternalAuthor.externalAuthor;
    QLikes qLikes = QLikes.likes;
    private final QFeed qFeed = QFeed.feed;
    private final QFeedSubscription qFeedSubscription = QFeedSubscription.feedSubscription;

    // 현재 로그인한 사용자 Id 조회
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }
        TokenBody tokenBody = (TokenBody) authentication.getPrincipal();
        return tokenBody.getMemberId();
    }

    // 검색 조건 설정
    private BooleanExpression buildSearchCondition(String searchType, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        switch (searchType) {
            case "title":
                return qPost.title.containsIgnoreCase(keyword);
            case "content":
                return qPost.content.containsIgnoreCase(keyword);
            case "author":
                return qUser.nickname.containsIgnoreCase(keyword)
                        .or(qExternalAuthor.name.containsIgnoreCase(keyword));
            case "titleContent":
                return qPost.title.containsIgnoreCase(keyword)
                        .or(qPost.content.containsIgnoreCase(keyword));
            default:
                return null;
        }
    }

    // 내가 구독한 PRIVATE 피드 ID 목록
    private List<Long> getSubscribedPrivateFeedIds(Long userId) {
        return queryFactory
                .select(qFeedSubscription.feed.id)
                .from(qFeedSubscription)
                .where(qFeedSubscription.user.id.eq(userId))
                .fetch();
    }

    // 구독한 PRIVATE 피드 조건
    private BooleanExpression subscribedPrivateCondition(List<Long> feedIds) {
        if (feedIds.isEmpty()) return null;
        return qPost.isPublic.isFalse().and(qPost.feed.id.in(feedIds));
    }

    // 로그인 사용자용 fetch 크기: offset + pageSize 만큼만 DB에서 읽음
    private Pageable fetchPageable(Pageable pageable) {
        int fetchSize = (int) pageable.getOffset() + pageable.getPageSize();
        return PageRequest.of(0, fetchSize);
    }

    // 두 리스트 합산 후 createdAt 내림차순 정렬 + 페이징
    private Page<PostDetailResponse> mergeAndPage(
            List<PostDetailResponse> list1,
            List<PostDetailResponse> list2,
            long total,
            Pageable pageable) {
        List<PostDetailResponse> merged = Stream.concat(list1.stream(), list2.stream())
                .sorted(Comparator.comparing(PostDetailResponse::getCreatedAt).reversed())
                .collect(Collectors.toList());
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        return new PageImpl<>(merged.subList(start, end), pageable, total);
    }

    private List<PostDetailResponse> getPostList(Pageable pageable, BooleanExpression finalCondition) {
        List<Long> ids = queryFactory
                .select(qPost.id)
                .from(qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .where(finalCondition)
                .orderBy(qPost.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (ids.isEmpty()) return List.of();

        return queryFactory
                .select(postDetailsProjection())
                .from(qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .leftJoin(qPost.feed, qFeed)
                .where(qPost.id.in(ids))
                .orderBy(qPost.createdAt.desc())
                .fetch();
    }

    private long getCappedTotalCount(BooleanExpression finalCondition) {
        Long count = queryFactory
                .select(qPost.count())
                .from(qPost)
                .where(finalCondition)
                .fetchOne();
        return count == null ? 0 : Math.min(count, 100_000);
    }

    private QBean<PostDetailResponse> postDetailsProjection() {
        return Projections.fields(
                PostDetailResponse.class,
                qPost.id,
                qPost.title,
                qPost.content,
                qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
                qPost.createdAt,
                qPost.likeCount
        );
    }

    private List<PostDetailResponse> getPostLikeList(Pageable pageable, BooleanExpression finalCondition) {
        return queryFactory
                .select(postDetailsProjection())
                .from(qLikes)
                .join(qLikes.post, qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .leftJoin(qPost.feed, qFeed)
                .where(finalCondition)
                .orderBy(qPost.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private long getCappedTotalLikeCount(BooleanExpression finalCondition) {
        Long count = queryFactory
                .select(qLikes.count())
                .from(qLikes)
                .join(qLikes.post, qPost)
                .where(finalCondition)
                .fetchOne();
        return count == null ? 0 : Math.min(count, 100_000);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> findAll(Pageable pageable) {
        Long userId = getCurrentUserId();

        if (userId == null) {
            // 비로그인: WHERE is_public = true + LIMIT → 복합 인덱스 탐
            List<PostDetailResponse> content = getPostList(pageable, qPost.isPublic.isTrue());
            long total = getCappedTotalCount(qPost.isPublic.isTrue());
            return new PageImpl<>(content, pageable, total);
        }

        // 로그인: 공개 글 + 구독 PRIVATE 글 각각 fetchSize만큼 조회 후 합산
        List<Long> feedIds = getSubscribedPrivateFeedIds(userId);
        Pageable fetch = fetchPageable(pageable);

        List<PostDetailResponse> publicPosts = getPostList(fetch, qPost.isPublic.isTrue());
        List<PostDetailResponse> privatePosts = feedIds.isEmpty() ? List.of()
                : getPostList(fetch, subscribedPrivateCondition(feedIds));

        long total = getCappedTotalCount(qPost.isPublic.isTrue())
                + (feedIds.isEmpty() ? 0 : getCappedTotalCount(subscribedPrivateCondition(feedIds)));

        return mergeAndPage(publicPosts, privatePosts, total, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetails(Long postId) {
        return queryFactory
                .select(postDetailsProjection())
                .from(qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .where(qPost.id.eq(postId))
                .fetchOne();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> searchPost(Pageable pageable, String searchType, String keyword) {
        BooleanExpression search = buildSearchCondition(searchType, keyword);
        Long userId = getCurrentUserId();

        if (userId == null) {
            BooleanExpression condition = qPost.isPublic.isTrue().and(search);
            return new PageImpl<>(getPostList(pageable, condition), pageable, getCappedTotalCount(condition));
        }

        List<Long> feedIds = getSubscribedPrivateFeedIds(userId);
        Pageable fetch = fetchPageable(pageable);

        List<PostDetailResponse> publicPosts = getPostList(fetch, qPost.isPublic.isTrue().and(search));
        List<PostDetailResponse> privatePosts = feedIds.isEmpty() ? List.of()
                : getPostList(fetch, subscribedPrivateCondition(feedIds).and(search));

        long total = getCappedTotalCount(qPost.isPublic.isTrue().and(search))
                + (feedIds.isEmpty() ? 0 : getCappedTotalCount(subscribedPrivateCondition(feedIds).and(search)));

        return mergeAndPage(publicPosts, privatePosts, total, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> findLikesPosts(Pageable pageable) {
        Long userId = getCurrentUserId();
        if (userId == null) return Page.empty();

        List<Long> feedIds = getSubscribedPrivateFeedIds(userId);
        Pageable fetch = fetchPageable(pageable);
        BooleanExpression myLike = qLikes.user.id.eq(userId);

        List<PostDetailResponse> publicPosts = getPostLikeList(fetch, myLike.and(qPost.isPublic.isTrue()));
        List<PostDetailResponse> privatePosts = feedIds.isEmpty() ? List.of()
                : getPostLikeList(fetch, myLike.and(subscribedPrivateCondition(feedIds)));

        long total = getCappedTotalLikeCount(myLike.and(qPost.isPublic.isTrue()))
                + (feedIds.isEmpty() ? 0 : getCappedTotalLikeCount(myLike.and(subscribedPrivateCondition(feedIds))));

        return mergeAndPage(publicPosts, privatePosts, total, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailResponse> searchLikePosts(Pageable pageable, String searchType, String keyword) {
        Long userId = getCurrentUserId();
        if (userId == null) return Page.empty();

        BooleanExpression search = buildSearchCondition(searchType, keyword);
        List<Long> feedIds = getSubscribedPrivateFeedIds(userId);
        Pageable fetch = fetchPageable(pageable);
        BooleanExpression myLike = qLikes.user.id.eq(userId);

        List<PostDetailResponse> publicPosts = getPostLikeList(fetch, myLike.and(qPost.isPublic.isTrue()).and(search));
        List<PostDetailResponse> privatePosts = feedIds.isEmpty() ? List.of()
                : getPostLikeList(fetch, myLike.and(subscribedPrivateCondition(feedIds)).and(search));

        long total = getCappedTotalLikeCount(myLike.and(qPost.isPublic.isTrue()).and(search))
                + (feedIds.isEmpty() ? 0 : getCappedTotalLikeCount(myLike.and(subscribedPrivateCondition(feedIds)).and(search)));

        return mergeAndPage(publicPosts, privatePosts, total, pageable);
    }
}

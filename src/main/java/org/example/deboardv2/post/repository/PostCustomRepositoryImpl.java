package org.example.deboardv2.post.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.QBean;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.likes.entity.QLikes;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.entity.QPost;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.domain.QFeed;
import org.example.deboardv2.rss.domain.QFeedSubscription;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.QExternalAuthor;
import org.example.deboardv2.user.entity.QUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
            return null; // 비로그인 사용자 처리
        }
        TokenBody tokenBody = (TokenBody) authentication.getPrincipal();
        return tokenBody.getMemberId();
    }

    // 검색 조건 설정
    private BooleanExpression buildSearchCondition(String searchType, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null; // 전체 조회
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

    /*
    * 공통 게시글 조회 조건
    * 1. public 피드의 게시글
    * 2. 우리의 게시글*/
    private BooleanExpression getVisibilityCondition() {
        BooleanExpression isPublic = qPost.feed.feedType.eq(FeedType.PUBLIC)
                .or(qPost.author.isNotNull());

        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            return isPublic.or(isSubscribed());
        }
        return isPublic;
    }

    // 구독 조건
    private BooleanExpression isSubscribed() {
        Long currentUserId = getCurrentUserId();
        return JPAExpressions
                .selectOne()
                .from(qFeedSubscription)
                .where(
                        qFeedSubscription.feed.eq(qPost.feed),
                        qFeedSubscription.user.id.eq(currentUserId)
                )
                .exists();
    }

    private List<PostDetails> getPostList(Pageable pageable, BooleanExpression finalCondition) {
        return queryFactory
                .select(postDetailsProjection())
                .from(qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .leftJoin(qPost.feed, qFeed)
                .where(finalCondition)
                .orderBy(qPost.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private long getTotalCount(BooleanExpression finalCondition) {
        long total = Optional.ofNullable(
                queryFactory
                        .select(qPost.count())
                        .from(qPost)
                        .leftJoin(qPost.author, qUser)
                        .leftJoin(qPost.feed, qFeed)
                        .where(finalCondition)
                        .fetchOne()
        ).orElse(0L);
        return total;
    }

    private QBean<PostDetails> postDetailsProjection() {
        return Projections.fields(
                PostDetails.class,
                qPost.id,
                qPost.title,
                qPost.content,
                qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
                qPost.createdAt,
                qPost.likeCount
        );
    }

    private List<PostDetails> getPostLikeList(Pageable pageable, BooleanExpression finalCondition) {
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

    private Long getTotalLikeCount(BooleanExpression finalCondition) {
        return Optional.ofNullable(
                queryFactory
                        .select(qLikes.count())
                        .from(qLikes)
                        .join(qLikes.post, qPost)
                        .leftJoin(qPost.feed, qFeed)
                        .where(finalCondition)
                        .fetchOne()
        ).orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetails> findAll(Pageable pageable) {
        List<PostDetails> content = getPostList(pageable, getVisibilityCondition());

        long total = getTotalCount(getVisibilityCondition());

        return new PageImpl<PostDetails>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetails getPostDetails(Long postId) {
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
    public Page<PostDetails> searchPost(Pageable pageable, String searchType, String keyword) {
        // 수정: 공용 게시글 조건
        BooleanExpression visibility = getVisibilityCondition();

        // 기존 검색 조건 유지
        BooleanExpression searchCondition = buildSearchCondition(searchType, keyword);

        BooleanExpression finalCondition = visibility.and(searchCondition);

        List<PostDetails> content = getPostList(pageable, finalCondition);

        long total = getTotalCount(finalCondition);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetails> findLikesPosts(Pageable pageable) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Page.empty();
        }
        BooleanExpression finalCondition = qLikes.user.id.eq(currentUserId).and(getVisibilityCondition());

        List<PostDetails> content = getPostLikeList(pageable, finalCondition);

        Long total = getTotalLikeCount(finalCondition);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetails> searchLikePosts(Pageable pageable, String searchType, String keyword) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Page.empty();
        }
        BooleanExpression finalCondition = qLikes.user.id.eq(currentUserId)
                .and(getVisibilityCondition())
                .and(buildSearchCondition(searchType, keyword));

        List<PostDetails> content = getPostLikeList(pageable, finalCondition);

        Long total = getTotalLikeCount(finalCondition);
        return new PageImpl<>(content, pageable, total);
    }
}

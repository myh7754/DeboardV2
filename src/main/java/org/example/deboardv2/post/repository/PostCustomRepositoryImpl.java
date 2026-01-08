package org.example.deboardv2.post.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.likes.entity.QLikes;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.entity.QPost;
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

//@Repository
//@RequiredArgsConstructor
//@Slf4j
//public class PostCustomRepositoryImpl implements PostCustomRepository {
//    private final JPAQueryFactory queryFactory;
//    QPost qPost = QPost.post;
//    QUser qUser = QUser.user;
//    QExternalAuthor qExternalAuthor = QExternalAuthor.externalAuthor;
//    QUserFeed qUserFeed = QUserFeed.userFeed;
//    QLikes qLikes = QLikes.likes;
//
//    public Long getCurrentUserId() {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        log.info("Current User: {}", authentication.getPrincipal());
//        if (authentication == null || authentication.getPrincipal().equals("anonymousUser")) {
//            return null; // 비로그인 사용자 처리
//        }
//        TokenBody tokenBody = (TokenBody) authentication.getPrincipal();
//        return tokenBody.getMemberId();
//    }
//
//    @Override
//    public Page<PostDetails> findAll(Pageable pageable) {
//        Long currentUserId = getCurrentUserId();
//
//        // 수정: 공용 게시글 조건 (사이트 글 + 외부 RSS)
//        BooleanExpression publicCondition =
//                qPost.feed.isNotNull()
//                        .or(qPost.author.isNotNull());
//
//        // 수정: 개인 피드 조건 (본인 글만)
//        BooleanExpression personalCondition =
//                currentUserId != null
//                        ? qPost.userFeed.user.id.eq(currentUserId)
//                        : null;
//
//        // 수정: 최종 where 조건
//        BooleanExpression finalCondition = (personalCondition == null)
//                ? publicCondition
//                : publicCondition.or(personalCondition);
//
//        List<PostDetails> content = queryFactory
////                Dto에 @QueryProjection 사용 안한경우
//                .select(Projections.fields(
//                        PostDetails.class,
//                        qPost.id,
//                        qPost.title,
//                        qPost.content,
//                        // nickname은 user든 externalAuthor든 둘 중 하나를 선택
//                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
//                        qPost.createdAt,
//                        qPost.likeCount
//                ))
//                .from(qPost)
//                .leftJoin(qPost.author, qUser) // 기존: inner join
//                .leftJoin(qPost.externalAuthor, qExternalAuthor)
//                .leftJoin(qPost.userFeed, qUserFeed)
//                .offset(pageable.getOffset())
//                .limit(pageable.getPageSize())
//                .where(finalCondition)
//                .orderBy(qPost.createdAt.desc())
//                .fetch();
//
////               Dto에 QueryProjection 사용한경우
////                .select(new QPostDto(post.title, post.content, user.nickname))
////                .from(post)
////                .join(post.author, user)
////                .fetch();
//        long total = Optional.ofNullable(
//                queryFactory
//                        .select(Wildcard.count)
//                        .from(qPost)
//                        .leftJoin(qPost.userFeed, qUserFeed)
//                        .where(finalCondition)
//                        .fetchOne()
//        ).orElse(0L);
//
//        return new PageImpl<PostDetails>(content, pageable, total);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public PostDetails getPostDetails(Long postId) {
//        return queryFactory
//                .select(Projections.fields(
//                        PostDetails.class,
//                        qPost.id,
//                        qPost.title,
//                        qPost.content,
//                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
//                        qPost.createdAt,
//                        qPost.likeCount
//                ))
//                .from(qPost)
//                .leftJoin(qPost.author, qUser)
//                .leftJoin(qPost.externalAuthor, qExternalAuthor)
//                .where(qPost.id.eq(postId))
//                .fetchOne();
//    }
//
//    private BooleanExpression buildCondition(String searchType, String keyword) {
//        if (keyword == null || keyword.isBlank()) {
//            return null; // 전체 조회
//        }
//        switch (searchType) {
//            case "title":
//                return qPost.title.containsIgnoreCase(keyword);
//            case "content":
//                return qPost.content.containsIgnoreCase(keyword);
//            case "author":
//                return qUser.nickname.containsIgnoreCase(keyword)
//                        .or(qExternalAuthor.name.containsIgnoreCase(keyword));
//            case "titleContent":
//                return qPost.title.containsIgnoreCase(keyword)
//                        .or(qPost.content.containsIgnoreCase(keyword));
//            default:
//                return null;
//        }
//
//    }
//
//    @Override
//    public Page<PostDetails> searchPost(Pageable pageable, String searchType, String keyword) {
//        Long currentUserId = getCurrentUserId();
//        // 수정: 공용 게시글 조건
//        BooleanExpression publicCondition =
//                qPost.feed.isNotNull()
//                        .or(qPost.author.isNotNull());
//
//        // 수정: 개인 피드 게시글
//        BooleanExpression personalCondition =
//                currentUserId != null
//                        ? qPost.userFeed.user.id.eq(currentUserId)
//                        : null;
//
//        // 수정: baseCondition
//        BooleanExpression baseCondition = (personalCondition == null)
//                ? publicCondition
//                : publicCondition.or(personalCondition);
//
//        // 기존 검색 조건 유지
//        BooleanExpression searchCondition = buildCondition(searchType, keyword);
//
//        // 수정: 검색 + 접근권한 결합
//        BooleanExpression finalCondition = (searchCondition == null)
//                ? baseCondition
//                : baseCondition.and(searchCondition);
//
//        List<PostDetails> results = queryFactory
//                .select(Projections.fields(
//                        PostDetails.class,
//                        qPost.id,
//                        qPost.title,
//                        qPost.content,
//                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
//                        qPost.createdAt,
//                        qPost.likeCount
//                ))
//                .from(qPost)
//                .leftJoin(qPost.author, qUser)
//                .leftJoin(qPost.externalAuthor, qExternalAuthor)
//                .leftJoin(qPost.userFeed, qUserFeed)
//                .where(finalCondition)
//                .offset(pageable.getOffset())
//                .limit(pageable.getPageSize())
//                .orderBy(qPost.createdAt.desc())
//                .fetch();
//
//        Long total = Optional.ofNullable(
//                queryFactory
//                        .select(Wildcard.count)
//                        .from(qPost)
//                        .leftJoin(qPost.author, qUser)
//                        .leftJoin(qPost.externalAuthor, qExternalAuthor)
//                        .leftJoin(qPost.userFeed, qUserFeed)
//                        .where(finalCondition)
//                        .fetchOne()
//        ).orElse(0L);
//
//        return new PageImpl<>(results, pageable, total);
//    }
//
//    @Override
//    public Page<PostDetails> findLikesPosts(Pageable pageable) {
//        Long currentUserId = getCurrentUserId();
//
//        if (currentUserId == null) {
//            return Page.empty();
//        }
//
//        List<PostDetails> content = queryFactory
//                .select(Projections.fields(
//                        PostDetails.class,
//                        qPost.id,
//                        qPost.title,
//                        qPost.content,
//                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
//                        qPost.createdAt,
//                        qPost.likeCount
//                ))
//                .from(qLikes)
//                .join(qLikes.post, qPost)
//                .leftJoin(qPost.author, qUser)
//                .leftJoin(qPost.externalAuthor, qExternalAuthor)
//                .where(qLikes.user.id.eq(currentUserId))
//                .offset(pageable.getOffset())
//                .limit(pageable.getPageSize())
//                .orderBy(qPost.createdAt.desc())
//                .fetch();
//
//        Long total = Optional.ofNullable(
//                queryFactory
//                        .select(Wildcard.count)
//                        .from(qLikes)
//                        .where(qLikes.user.id.eq(currentUserId))
//                        .fetchOne()
//        ).orElse(0L);
//
//        return new PageImpl<>(content, pageable, total);
//    }
//
//    @Override
//    public Page<PostDetails> searchLikePosts(Pageable pageable, String searchType, String keyword) {
//        Long currentUserId = getCurrentUserId();
//        if (currentUserId == null) {
//            return Page.empty();
//        }
//        // 좋아요 기본 조건
//        BooleanExpression likeCondition = qLikes.user.id.eq(currentUserId);
//
//        // 공용 게시글 조건
//        BooleanExpression publicCondition =
//                qPost.feed.isNotNull()
//                        .or(qPost.author.isNotNull());
//
//        // 개인 피드 게시글
//        BooleanExpression personalCondition =
//                currentUserId != null
//                        ? qPost.userFeed.user.id.eq(currentUserId)
//                        : null;
//
//        // baseCondition
//        BooleanExpression baseCondition = (personalCondition == null)
//                ? publicCondition
//                : publicCondition.or(personalCondition);
//
//        // 기존 검색 조건 유지
//        BooleanExpression searchCondition = buildCondition(searchType, keyword);
//
//        // 최종 where 조건 결합
//        BooleanExpression finalCondition = likeCondition
//                .and(baseCondition)
//                .and(searchCondition != null ? searchCondition : null);
//
//        List<PostDetails> content = queryFactory
//                .select(Projections.fields(
//                        PostDetails.class,
//                        qPost.id,
//                        qPost.title,
//                        qPost.content,
//                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
//                        qPost.createdAt,
//                        qPost.likeCount
//                ))
//                .from(qLikes)
//                .join(qLikes.post, qPost)
//                .leftJoin(qPost.author , qUser)
//                .leftJoin(qPost.externalAuthor, qExternalAuthor)
//                .leftJoin(qPost.userFeed, qUserFeed)
//                .where(finalCondition)
//                .offset(pageable.getOffset())
//                .limit(pageable.getPageSize())
//                .orderBy(qPost.createdAt.desc())
//                .fetch();
//
//        Long total = Optional.ofNullable(
//                queryFactory
//                        .select(Wildcard.count)
//                        .from(qLikes)
//                        .join(qLikes.post, qPost)
//                        .leftJoin(qPost.userFeed, qUserFeed)
//                        .where(finalCondition)
//                        .fetchOne()
//        ).orElse(0L);
//
//        return new PageImpl<>(content, pageable, total);
//    }
//
//
//}

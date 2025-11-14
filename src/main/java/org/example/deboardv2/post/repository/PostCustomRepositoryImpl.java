package org.example.deboardv2.post.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.entity.QPost;
import org.example.deboardv2.rss.domain.QUserFeed;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.QExternalAuthor;
import org.example.deboardv2.user.entity.QUser;
import org.example.deboardv2.user.service.UserService;
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
    QUserFeed qUserFeed = QUserFeed.userFeed;

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Current User: {}", authentication.getPrincipal());
        if (authentication == null || authentication.getPrincipal().equals("anonymousUser")) {
            return null; // 비로그인 사용자 처리
        }
        TokenBody tokenBody = (TokenBody) authentication.getPrincipal();
        return tokenBody.getMemberId();
    }

    @Override
    public Page<PostDetails> findAll(Pageable pageable) {
        Long currentUserId = getCurrentUserId();
        BooleanExpression baseCondition =
                qPost.feed.isNotNull() // 공통 피드
                        .or(qPost.author.isNotNull()); // 직접 작성글

        // 만약 현재 로그인된 사용자가 있다면 해당 사용자는 등록한 rss글까지 불러옴
        if (currentUserId != null) {
            baseCondition = baseCondition.or(qUserFeed.user.id.eq(currentUserId));
        }

        List<PostDetails> content = queryFactory
//                Dto에 @QueryProjection 사용 안한경우
                .select(Projections.fields(
                        PostDetails.class,
                        qPost.id,
                        qPost.title,
                        qPost.content,
                        // nickname은 user든 externalAuthor든 둘 중 하나를 선택
                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
                        qPost.createdAt,
                        qPost.likeCount
                ))
                .from(qPost)
                .leftJoin(qPost.author, qUser) // 기존: inner join
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .leftJoin(qPost.userFeed, qUserFeed)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .where(baseCondition)
                .orderBy(qPost.createdAt.desc())
                .fetch();

//               Dto에 QueryProjection 사용한경우
//                .select(new QPostDto(post.title, post.content, user.nickname))
//                .from(post)
//                .join(post.author, user)
//                .fetch();
        long total = Optional.ofNullable(
                queryFactory
                        .select(Wildcard.count)
                        .from(qPost)
                        .leftJoin(qPost.userFeed, qUserFeed)
                        .where(baseCondition)
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<PostDetails>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetails getPostDetails(Long postId) {
        return queryFactory
                .select(Projections.fields(
                        PostDetails.class,
                        qPost.id,
                        qPost.title,
                        qPost.content,
                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
                        qPost.createdAt,
                        qPost.likeCount
                ))
                .from(qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .where(qPost.id.eq(postId))
                .fetchOne();
    }

    private BooleanExpression buildCondition(String searchType, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null; // 전체 조회
        }
        switch (searchType) {
            case "title":
                return qPost.title.containsIgnoreCase(keyword);
            case "content":
                return qPost.content.containsIgnoreCase(keyword);
            case "author":
                return qUser.nickname.containsIgnoreCase(keyword);
            case "titleContent":
                return qPost.title.containsIgnoreCase(keyword)
                        .or(qPost.content.containsIgnoreCase(keyword));
            default:
                return null;
        }

    }

    @Override
    public Page<PostDetails> searchPost(Pageable pageable, String searchType, String keyword) {
        Long currentUserId = getCurrentUserId();
        BooleanExpression baseCondition =
                qPost.feed.isNotNull() // 공통 피드
                        .or(qPost.author.isNotNull()); // 직접 작성글

        // 만약 현재 로그인된 사용자가 있다면 해당 사용자는 등록한 rss글까지 불러옴
        if (currentUserId != null) {
            baseCondition = baseCondition.or(qUserFeed.user.id.eq(currentUserId));
        }

        // 2. 검색 조건
        BooleanExpression searchCondition = buildCondition(searchType, keyword);

        // 3. 최종 조건: baseCondition AND searchCondition
        BooleanExpression finalCondition = baseCondition.and(searchCondition);
        List<PostDetails> results = queryFactory
                .select(Projections.fields(
                        PostDetails.class,
                        qPost.id,
                        qPost.title,
                        qPost.content,
                        qUser.nickname.coalesce(qExternalAuthor.name).as("nickname"),
                        qPost.createdAt,
                        qPost.likeCount
                ))
                .from(qPost)
                .leftJoin(qPost.author, qUser)
                .leftJoin(qPost.externalAuthor, qExternalAuthor)
                .leftJoin(qPost.userFeed, qUserFeed)
                .where(finalCondition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(qPost.createdAt.desc())
                .fetch();

        Long total = Optional.ofNullable(
                queryFactory
                        .select(Wildcard.count)
                        .from(qPost)
                        .leftJoin(qPost.author, qUser)
                        .leftJoin(qPost.externalAuthor, qExternalAuthor)
                        .leftJoin(qPost.userFeed, qUserFeed)
                        .where(finalCondition)
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(results, pageable, total);
    }
}

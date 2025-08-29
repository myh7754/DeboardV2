package org.example.deboardv2.post.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.entity.QPost;
import org.example.deboardv2.user.entity.QUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    @Override
    public Page<PostDetails> findAll(Pageable pageable) {

        List<PostDetails> content = queryFactory
//                Dto에 @QueryProjection 사용 안한경우
                .select(Projections.fields(
                        PostDetails.class,
                        qPost.id,
                        qPost.title,
                        qPost.content,
                        qUser.nickname,
                        qPost.createdAt
                ))
                .from(qPost)
                .join(qPost.author, qUser)
                .orderBy(qPost.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
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
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<PostDetails>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetails getPostDetails(Long postId) {
        return queryFactory.
                select(Projections.constructor(PostDetails.class,
                        qPost.id,
                        qPost.title,
                        qPost.content,
                        qUser.nickname,
                        qPost.createdAt
                        ))
                .from(qPost)
                .join(qPost.author, qUser)
                .where(qPost.id.eq(postId))
                .fetchOne();
    }
}

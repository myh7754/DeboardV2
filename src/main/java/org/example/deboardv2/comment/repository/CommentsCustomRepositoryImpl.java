package org.example.deboardv2.comment.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.deboardv2.comment.dto.CommentsDetail;
import org.example.deboardv2.comment.entity.QComments;
import org.example.deboardv2.user.entity.QUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommentsCustomRepositoryImpl implements CommentsCustomRepository {
    private final JPAQueryFactory queryFactory;
    QComments qComments =  QComments.comments;
    QUser qUser = QUser.user;
    QComments qChildren = new QComments("children"); // 대댓글 alias


    @Override
    public Page<CommentsDetail> findAll(Long postId, Pageable pageable) {
        List<CommentsDetail> content = queryFactory
                .select(Projections.fields(
                        CommentsDetail.class,
                        qComments.commentsId.as("commentsId"), // <- alias 지정
                        qComments.content,
                        qComments.createdAt,
                        qUser.nickname.as("author"),          // <- alias 지정
                        qComments.parent.commentsId.as("parentId"), // <- alias 지정
                        qChildren.commentsId.countDistinct().as("repliesCount")
                ))
                .from(qComments)
                .join(qComments.author, qUser)
                .leftJoin(qComments.children, qChildren)
                .where(qComments.post.id.eq(postId)
                        .and(qComments.parent.isNull())) // 부모댓글만 호출
                .groupBy(qComments.commentsId,
                        qComments.content,
                        qComments.createdAt,
                        qUser.nickname,
                        qComments.parent.commentsId)
                .orderBy(qComments.commentsId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = Optional.ofNullable(
                queryFactory
                        .select(Wildcard.count)
                        .from(qComments)
                        .where(qComments.post.id.eq(postId)
                                .and(qComments.parent.isNull())) // 부모 댓글만 count
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(content,pageable,total);
    }
    
    // 특정 댓글의 대댓글 조회
    public Page<CommentsDetail> findReplies(Long parentId, Pageable pageable) {
        List<CommentsDetail> replies = queryFactory
                .select(Projections.constructor(
                        CommentsDetail.class,
                        qComments.commentsId.as("commentsId"),
                        qComments.content,
                        qComments.createdAt,
                        qUser.nickname.as("author"),
                        qComments.parent.commentsId.as("parentId"),
                        Expressions.asNumber(0L) // 대댓글은 항상 0
                ))
                .from(qComments)
                .join(qComments.author, qUser)
                .where(qComments.parent.commentsId.eq(parentId))
                .orderBy(qComments.commentsId.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = Optional.ofNullable(
                queryFactory
                        .select(Wildcard.count)
                        .from(qComments)
                        .where(qComments.parent.commentsId.eq(parentId))
                        .fetchOne()
        ).orElse(0L);

        return new PageImpl<>(replies, pageable, total);
    }
}

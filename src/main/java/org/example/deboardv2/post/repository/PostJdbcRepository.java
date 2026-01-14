package org.example.deboardv2.post.repository;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.post.entity.Post;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final PostRepository postRepository;


    @Transactional
    public void saveBatch(List<Post> posts) {
        String sql = "INSERT INTO post (title, content, image, link, external_author_id," +
                "created_at, feed_id,like_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Post post = posts.get(i);
                ps.setString(1, post.getTitle());
                ps.setString(2, post.getContent());
                ps.setString(3, post.getImage());
                ps.setString(4, post.getLink());
                ps.setObject(5, post.getExternalAuthor().getId());
                ps.setObject(6, post.getCreatedAt());
                ps.setObject(7, post.getFeed().getId());
                ps.setInt(8, post.getLikeCount());
            }

            @Override
            public int getBatchSize() {
                return posts.size();
            }
        });
    }

//    @Transactional
//    public void saveBatch(List<Post> lists) {
//        postRepository.saveAll(lists);
//    }


}

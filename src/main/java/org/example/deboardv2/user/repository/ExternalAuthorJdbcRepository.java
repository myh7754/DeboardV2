package org.example.deboardv2.user.repository;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ExternalAuthorJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ExternalAuthorRepository externalAuthorRepository;

    @Transactional
    public List<ExternalAuthor> saveBatch(List<ExternalAuthor> authorList) {
        String sql = "INSERT INTO external_author (name, source_url) VALUES (?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setString(1, authorList.get(i).getName());
                ps.setString(2, authorList.get(i).getSourceUrl());
            }

            @Override
            public int getBatchSize() {
                return authorList.size();
            }
        });
        return authorList;
    }

//    @Transactional
//    public List<ExternalAuthor> saveBatch(List<ExternalAuthor> lists) {
//        return externalAuthorRepository.saveAll(lists);
//    }



}

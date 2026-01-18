package org.example.deboardv2.user.repository;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ExternalAuthorJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ExternalAuthorRepository externalAuthorRepository;
//
    @Transactional
    public List<ExternalAuthor> saveBatch(List<ExternalAuthor> authorList) {
        String sql = "INSERT INTO external_author (name, source_url) VALUES (?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.batchUpdate(
                connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, authorList.get(i).getName());
                        ps.setString(2, authorList.get(i).getSourceUrl());
                    }

                    @Override
                    public int getBatchSize() {
                        return authorList.size();
                    }
                },
                keyHolder
        );

        // 생성된 키를 authorList에 매핑
        List<Map<String, Object>> keyList = keyHolder.getKeyList();
        for (int i = 0; i < authorList.size(); i++) {
            Long id = ((Number) keyList.get(i).get("GENERATED_KEY")).longValue();
            authorList.get(i).setId(id);
        }

        return authorList;
    }

//    @Transactional
//    public List<ExternalAuthor> saveBatch(List<ExternalAuthor> lists) {
//        return externalAuthorRepository.saveAll(lists);
//    }

}

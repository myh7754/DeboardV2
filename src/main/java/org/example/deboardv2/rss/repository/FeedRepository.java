package org.example.deboardv2.rss.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.deboardv2.rss.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FeedRepository extends JpaRepository<Feed,Long> {
    boolean existsByFeedUrl(String resolve);

    Optional<Feed> findByFeedUrl(String resolve);

    List<Feed> findAllByIsActiveTrue();

    @Modifying // SELECT가 아닌 데이터를 변경하는 쿼리임을 명시
    @Transactional // 데이터 변경을 위해 트랜잭션 필요
    @Query("UPDATE Feed f SET f.isActive = false WHERE f.id IN :ids")
    void disableFeedsByIds(@Param("ids") List<Long> ids);
}

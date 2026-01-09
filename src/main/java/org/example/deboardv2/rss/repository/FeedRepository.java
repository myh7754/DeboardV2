package org.example.deboardv2.rss.repository;

import org.example.deboardv2.rss.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedRepository extends JpaRepository<Feed,Long> {
    boolean existsByFeedUrl(String resolve);

    Optional<Feed> findByFeedUrl(String resolve);
}

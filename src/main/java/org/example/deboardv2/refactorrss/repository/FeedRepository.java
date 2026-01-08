package org.example.deboardv2.refactorrss.repository;

import org.example.deboardv2.refactorrss.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed,Long> {
    boolean existsByFeedUrl(String resolve);
}

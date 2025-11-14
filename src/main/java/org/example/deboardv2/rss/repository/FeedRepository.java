package org.example.deboardv2.rss.repository;

import org.example.deboardv2.rss.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedRepository extends JpaRepository<Feed,Long> {

    List<Feed> findByFeedUrl(String feedURL);
}

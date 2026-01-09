package org.example.deboardv2.rss.repository;

import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedSubscription;
import org.example.deboardv2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedSubscriptionRepository extends JpaRepository<FeedSubscription,Long> {

    boolean existsByUserAndFeed(User referenceUser, Feed feed);
}

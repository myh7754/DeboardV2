package org.example.deboardv2.rss.repository;

import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedSubscription;
import org.example.deboardv2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeedSubscriptionRepository extends JpaRepository<FeedSubscription,Long> {

    boolean existsByUserAndFeed(User referenceUser, Feed feed);

    List<FeedSubscription> findAllByUser(User user);

    boolean existsByFeed(Feed feed);
}

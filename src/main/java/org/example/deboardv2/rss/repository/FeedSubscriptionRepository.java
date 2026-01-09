package org.example.deboardv2.rss.repository;

import org.example.deboardv2.rss.domain.FeedSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedSubscriptionRepository extends JpaRepository<FeedSubscription,Long> {
}

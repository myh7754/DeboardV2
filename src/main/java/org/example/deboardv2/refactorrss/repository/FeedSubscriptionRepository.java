package org.example.deboardv2.refactorrss.repository;

import org.example.deboardv2.refactorrss.domain.FeedSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedSubscriptionRepository extends JpaRepository<FeedSubscription,Long> {
}

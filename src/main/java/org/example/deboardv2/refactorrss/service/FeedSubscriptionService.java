package org.example.deboardv2.refactorrss.service;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.refactorrss.domain.FeedSubscription;
import org.example.deboardv2.refactorrss.repository.FeedSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedSubscriptionService {
    private final FeedSubscriptionRepository subscriptionRepository;

    @Transactional
    public void registerFeedSubscription(FeedSubscription subscription) {
        subscriptionRepository.save(subscription);
    }
}

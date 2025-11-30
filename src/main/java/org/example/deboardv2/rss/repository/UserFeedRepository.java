package org.example.deboardv2.rss.repository;

import org.example.deboardv2.rss.domain.UserFeed;
import org.example.deboardv2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFeedRepository extends JpaRepository<UserFeed,Long> {

    Optional<UserFeed> findByFeedUrl(String feedUrl);

    List<UserFeed> findAllByUser(User user);
    boolean existsByUserAndFeedUrl(User user, String feedUrl);
}

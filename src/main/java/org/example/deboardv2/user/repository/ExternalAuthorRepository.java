package org.example.deboardv2.user.repository;

import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface ExternalAuthorRepository extends JpaRepository<ExternalAuthor,Long> {
    List<ExternalAuthor> findAllByNameInAndFeed(Set<String> authorNames, Feed feed);

    List<ExternalAuthor> findByNameIn(Set<String> authorNames);
}

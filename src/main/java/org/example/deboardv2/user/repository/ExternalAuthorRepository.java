package org.example.deboardv2.user.repository;

import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExternalAuthorRepository extends JpaRepository<ExternalAuthor,Long> {
    List<ExternalAuthor> findAllByNameInAndSourceUrl(Set<String> authorNames, String rssFeedUrl);
}

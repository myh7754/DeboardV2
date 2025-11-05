package org.example.deboardv2.user.repository;

import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExternalAuthorRepository extends JpaRepository<ExternalAuthor,Long> {
    Optional<ExternalAuthor> findByNameAndSourceUrl(String name, String sourceUrl);
}

package org.example.deboardv2.user.repository;

import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface ExternalAuthorRepository extends JpaRepository<ExternalAuthor,Long> {
    Optional<ExternalAuthor> findByNameAndSourceUrl(String name, String sourceUrl);
    
    // 배치 조회: 여러 author를 한 번에 조회 (성능 개선)
    @Query("SELECT ea FROM ExternalAuthor ea WHERE ea.sourceUrl = :sourceUrl AND ea.name IN :names")
    List<ExternalAuthor> findBySourceUrlAndNamesIn(@Param("sourceUrl") String sourceUrl, @Param("names") Set<String> names);
}

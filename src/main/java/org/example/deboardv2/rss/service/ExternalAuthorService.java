package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.repository.ExternalAuthorJdbcRepository;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalAuthorService {
    private final ExternalAuthorRepository externalAuthorRepository;
    private final ExternalAuthorJdbcRepository externalAuthorJdbcRepository;

    @Transactional
    public Map<String, ExternalAuthor> prepareAuthors(List<RssPost> rssPosts, String rssFeedUrl) {
        Set<String> authorNames= extractAuthorNames(rssPosts);
        Map<String, ExternalAuthor> authorMap = findExistingAuthors(authorNames, rssFeedUrl);
        saveNewAuthors(authorNames, authorMap, rssFeedUrl);
        return authorMap;
    }

    // rssPost에서 중복 닉네임 제거
    private Set<String> extractAuthorNames(List<RssPost> rssPosts) {
        return rssPosts.stream()
                .map(RssPost::getAuthor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // 이미 존재하는 작가들은 캐시로 사용하기 위해 Map로 불러옴
    private Map<String, ExternalAuthor> findExistingAuthors(Set<String> authorNames, String rssFeedUrl) {
        log.info("작가들 불러오기 어떤피드의 작가들? {}", rssFeedUrl);
        return externalAuthorRepository.findAllByNameInAndSourceUrl(authorNames,rssFeedUrl)
                .stream()
                .collect(Collectors.toMap(ExternalAuthor::getName, a -> a));

    }

    // 새로운 글쓴이를 추출하여 저장
    private void saveNewAuthors(Set<String> allNames, Map<String, ExternalAuthor> authorMap, String rssFeedUrl) {
        List<ExternalAuthor> newAuthors = allNames.stream()
                .filter(name -> !authorMap.containsKey(name))
                .map(name -> createAuthorEntity(name,rssFeedUrl))
                .toList();

        if (!newAuthors.isEmpty()) {
            List<ExternalAuthor> savedAuthors = batchSaveAuthors(newAuthors);
            savedAuthors.forEach(a -> authorMap.put(a.getName(), a));
        }
    }

    // ExternalAuthor을 배치처리 저장
    private List<ExternalAuthor> batchSaveAuthors(List<ExternalAuthor> newAuthors) {
        return externalAuthorJdbcRepository.saveBatch(newAuthors);
//        return externalAuthorRepository.saveAll(newAuthors);
    }

    private ExternalAuthor createAuthorEntity(String name, final String rssFeedUrl) {
        ExternalAuthor author = new ExternalAuthor();
        author.update(name, rssFeedUrl);
        return author;
    }
}

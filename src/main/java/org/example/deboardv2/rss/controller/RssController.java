package org.example.deboardv2.rss.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.example.deboardv2.rss.dto.FeedDto;
import org.example.deboardv2.rss.dto.UserFeedDto;
import org.example.deboardv2.rss.service.Impl.RssService;
import org.example.deboardv2.rss.service.Impl.TistoryRssParser;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rss")
public class RssController {
    private final UserService userService;
    private final RssService rssService;
    private final RssParserStrategy parserStrategy;

    @PostMapping("/feed")
    public ResponseEntity<?> registerFeed(@RequestParam String name, @RequestParam String url) throws Exception {
        Feed feed = rssService.registerFeed(name, url);
        rssService.fetchRssFeed(feed.getFeedUrl(), feed);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/feed")
    public ResponseEntity<?> getAllFeeds() {
        List<Feed> feeds = rssService.getAllFeeds();
        List<FeedDto> feedDtos = feeds.stream()
                .map(uf -> new FeedDto(
                        uf.getId(),
                        uf.getSiteName(),
                        uf.getFeedUrl()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(feedDtos);
    }

    @PostMapping("/user-feed")
    public ResponseEntity<?> registerUserFeed(@RequestParam String name, @RequestParam String url) throws Exception {
        UserFeed userFeed = rssService.registerUserFeed(name, url);
        rssService.fetchRssFeed(userFeed.getFeedUrl(), userFeed);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user-feed")
    public ResponseEntity<?> getAllUserFeeds() {
        User currentUser = userService.getCurrentUser();
        List<UserFeed> userFeeds = rssService.getUserFeeds(currentUser);
        List<UserFeedDto> userFeedDtos = userFeeds.stream()
                .map(uf -> new UserFeedDto(
                        uf.getId(),
                        uf.getSiteName(),
                        uf.getFeedUrl()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(userFeedDtos);
    }

    @DeleteMapping("/user-feed/{id}")
    public ResponseEntity<?> deleteUserFeed(@PathVariable Long id) throws Exception {
        rssService.deleteUserFeed(id);
        return ResponseEntity.ok().build();
    }
}

package org.example.deboardv2.rss.controller;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.example.deboardv2.rss.service.Impl.RssService;
import org.example.deboardv2.rss.service.Impl.TistoryRssParser;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        rssService.fetchRssFeed(feed.getFeedURL(),feed);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/feed")
    public ResponseEntity<?> getAllFeeds() {
        return ResponseEntity.ok(rssService.getAllFeeds());
    }

    @PostMapping("/user-feed")
    public ResponseEntity<?> registerUserFeed(@RequestParam String name, @RequestParam String url) throws Exception {
        UserFeed userFeed = rssService.registerUserFeed(name, url);
        rssService.fetchRssFeed(userFeed.getFeedUrl(),userFeed);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user-feed")
    public ResponseEntity<?> getAllUserFeeds() {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(rssService.getUserFeeds(currentUser));
    }
}

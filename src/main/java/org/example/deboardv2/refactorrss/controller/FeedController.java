package org.example.deboardv2.refactorrss.controller;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.refactorrss.domain.Feed;
import org.example.deboardv2.refactorrss.domain.FeedType;
import org.example.deboardv2.refactorrss.domain.RssPost;
import org.example.deboardv2.refactorrss.parser.RssParserStrategy;
import org.example.deboardv2.refactorrss.service.FeedService;
import org.example.deboardv2.refactorrss.service.RssFetchService;
import org.example.deboardv2.refactorrss.service.RssParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rss")
public class FeedController {

    @PostMapping("/feed")
    public ResponseEntity<?> addFeed (@RequestParam String name, @RequestParam String url) throws Exception {

        return ResponseEntity.ok().build();
    }
    @GetMapping("/feed")
    public ResponseEntity<?> getAllFeeds() {
        return ResponseEntity.ok(null);
    }
    @PostMapping("/user-feed")
    public ResponseEntity<?> addUserFeed (@RequestParam String name, @RequestParam String url) {

        return ResponseEntity.ok().build();
    }

    @GetMapping("/user-feed")
    public ResponseEntity<?> getAllUserFeeds() {
        return ResponseEntity.ok(null);
    }

}

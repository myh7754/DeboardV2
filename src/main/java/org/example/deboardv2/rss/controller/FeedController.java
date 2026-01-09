package org.example.deboardv2.rss.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.service.FeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rss")
public class FeedController {
    private final FeedService feedService;

    @PostMapping("/feed")
    public ResponseEntity<?> addFeed (@RequestParam String name, @RequestParam String url) throws Exception {
        feedService.registerFeed(name, url);
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

package org.example.deboardv2.likes.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/likes")
public class LikesController {
    private final LikeService likeService;
    private final UserService userService;
    private final PostService postService;

    @Operation(summary = "좋아요 요청", description = "게시글 좋아요 요청.")
    @PostMapping("/{postId}")
    public ResponseEntity<?> toggleLike(@PathVariable("postId") Long postId) {
        likeService.toggleLike(postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "좋아요 조회", description = "게시글 좋아요 여부 조회")
    @GetMapping("/{postId}")
    public ResponseEntity<?> getLike(@PathVariable("postId") Long postId) {
        return ResponseEntity.ok().body(likeService.getLikeStatus(postId));
    }

}

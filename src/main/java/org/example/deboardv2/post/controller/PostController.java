package org.example.deboardv2.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.post.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PostController {
    private final PostService postService;
    @GetMapping("/posts")
    public ResponseEntity<?> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        // sort = createdAt, desc -> ["createdAt", "eesc"]
        Page<PostDetails> postDtos = postService.readAll(size, page);
        return ResponseEntity.ok(postDtos);
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<?> getPosts(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPostDtoById(postId));
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@RequestBody PostCreateDto postDto) {
        return ResponseEntity.ok(postService.save(postDto));
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<?> updatePost(@RequestBody PostUpdateDto postDto, @PathVariable Long postId) {
        postService.update(postDto, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long postId) {
        postService.delete(postId);
        return ResponseEntity.ok().build();
    }

}

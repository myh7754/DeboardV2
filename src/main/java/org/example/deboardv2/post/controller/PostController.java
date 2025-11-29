package org.example.deboardv2.post.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.dto.PostDetails;
import org.example.deboardv2.post.dto.PostUpdateDto;
import org.example.deboardv2.rss.service.RssService;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.search.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PostController {
    private final PostService postService;
    private final SearchService searchService;
    private final RssService rssService;

    @Operation(summary = "게시글 목록 조회", description = "검색된 혹은 모든 게시글을 조회합니다.")
    @GetMapping("/posts")
    public ResponseEntity<?> getAllPosts(
            @RequestParam(defaultValue = "title", required = false) String searchType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        // sort = createdAt, desc -> ["createdAt", "eesc"]
        Page<PostDetails> postDtos;
        if (keyword == null || keyword.isBlank()) {
            postDtos = postService.readAll(size, page);
        }  else {
            postDtos = searchService.search(searchType, keyword, page, size);
        }
        return ResponseEntity.ok(postDtos);
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 게시글을 조회합니다.")
    @GetMapping("/posts/{postId}")
    public ResponseEntity<?> getPosts(@PathVariable Long postId) {
        PostDetails postDtoById = postService.getPostDtoById(postId);
        log.debug("postDtoById:{}", postDtoById);
        return ResponseEntity.ok(postDtoById);
    }

    @Operation(summary = "게시글 등록", description = "새로운 게시글을 작성합니다.")
    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@RequestBody PostCreateDto postDto) {
        return ResponseEntity.ok(postService.save(postDto));
    }

    @Operation(summary = "게시글 수정", description = "게시글을 수정합니다.")
    @PutMapping("/posts/{postId}")
    public ResponseEntity<?> updatePost(@RequestBody PostUpdateDto postDto, @PathVariable Long postId) {
        postService.update(postDto, postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long postId) {
        postService.delete(postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "좋아요한 게시글 목록", description = "로그인된 사용자의 게시글을 가져옵니다")
    @GetMapping("/posts/liked")
    public ResponseEntity<?> getLikedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(postService.readAll(page, size));
    }
}

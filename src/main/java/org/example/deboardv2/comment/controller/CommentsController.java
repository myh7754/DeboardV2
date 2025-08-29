package org.example.deboardv2.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.dto.CommentsDetail;
import org.example.deboardv2.comment.dto.CommentsRequest;
import org.example.deboardv2.comment.service.CommentsService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.xml.stream.events.Comment;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentsController {
    private final CommentsService commentsService;

    @GetMapping("/{postId}")
    public ResponseEntity<?> getComments(@PathVariable Long postId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {

        Page<CommentsDetail> commentsDetails = commentsService.readComments(postId, size, page);
        return ResponseEntity.ok(commentsDetails);
    }
    @GetMapping("/{postId}/replies")
    public ResponseEntity<?> getReplies(@PathVariable Long postId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size
    ) {
        Page<CommentsDetail> replies = commentsService.replies(postId, size, page);
        return ResponseEntity.ok(replies);
    }

    @PostMapping
    public ResponseEntity<?> createComment(@RequestBody CommentsRequest comment) {
        log.info("comments: {}", comment);
        commentsService.createComments(comment);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable Long id) {
        commentsService.deleteComments(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateComment(@RequestBody CommentsRequest comment, @PathVariable Long id) {
        commentsService.updateComments(comment, id);
        return ResponseEntity.ok().build();
    }
}

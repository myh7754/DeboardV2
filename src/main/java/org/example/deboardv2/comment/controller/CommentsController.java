package org.example.deboardv2.comment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Comments", description = "댓글 관련 API")
public class CommentsController {
    private final CommentsService commentsService;

    //    @ApiResponse(responseCode = "200", description = "성공",
//            content = @Content(schema = @Schema(implementation = CommentsDetail.class)))
    // apiResponse는 응답형식을 내가 정해서 보낼 필요성이 있을 때 사용
    @Operation(summary = "게시글의 댓글 조회", description = "특정 게시글의 모든 댓글을 페이징 조회합니다.")
    @GetMapping("/{postId}")
    public ResponseEntity<?> getComments(@PathVariable Long postId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {

        Page<CommentsDetail> commentsDetails = commentsService.readComments(postId, size, page);
        return ResponseEntity.ok(commentsDetails);
    }

    @Operation(summary = "댓글의 대댓글 조회", description = "특정 댓글의 대댓글을 페이징 조회합니다.")
    @GetMapping("/{commentsId}/replies")
    public ResponseEntity<?> getReplies(@PathVariable Long commentsId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size
    ) {
        Page<CommentsDetail> replies = commentsService.replies(commentsId, size, page);
        return ResponseEntity.ok(replies);
    }

    @Operation(summary = "댓글 작성", description = "새로운 댓글을 작성합니다.")
    @PostMapping
    public ResponseEntity<?> createComment(@RequestBody CommentsRequest comment) {
        log.info("comments: {}", comment);
        commentsService.createComments(comment);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 삭제", description = "댓글 ID로 댓글을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable Long id) {
        commentsService.deleteComments(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 수정", description = "댓글 ID로 댓글 내용을 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateComment(@RequestBody CommentsRequest comment, @PathVariable Long id) {
        commentsService.updateComments(comment, id);
        return ResponseEntity.ok().build();
    }
}

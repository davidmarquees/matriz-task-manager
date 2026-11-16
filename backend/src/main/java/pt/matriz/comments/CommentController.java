package pt.matriz.comments;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pt.matriz.comments.CommentService.Comment;
import pt.matriz.security.AuthenticatedUser;

/** Comments operations of openapi.yaml. */
@RestController
class CommentController {

    record CommentCreateRequest(String body) {
    }

    private final CommentService comments;

    CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping("/tasks/{taskId}/comments")
    List<Comment> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long taskId) {
        return comments.list(user, taskId);
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Comment add(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long taskId,
                @RequestBody CommentCreateRequest body) {
        return comments.add(user, taskId, body.body());
    }
}

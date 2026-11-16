package pt.matriz.tasks;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import pt.matriz.security.AuthenticatedUser;
import pt.matriz.tasks.TaskViews.Task;
import pt.matriz.tasks.TaskViews.TaskPage;
import pt.matriz.web.PatchBody;

/** Tasks operations of openapi.yaml. HTTP only; the rules live in {@link TaskService}. */
@RestController
class TaskController {

    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/projects/{projectId}/tasks")
    TaskPage list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                  @RequestParam(required = false) String q,
                  @RequestParam(required = false) String status,
                  @RequestParam(required = false) String priority,
                  @RequestParam(required = false) Long assigneeId,
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size) {
        return tasks.list(user, projectId, q, status, priority, assigneeId, page, size);
    }

    @PostMapping("/projects/{projectId}/tasks")
    ResponseEntity<Task> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                @RequestBody TaskService.NewTask body) {
        Task created = tasks.create(user, projectId, body);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/tasks/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/tasks/{taskId}")
    Task get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long taskId) {
        return tasks.get(user, taskId);
    }

    @PatchMapping("/tasks/{taskId}")
    Task update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long taskId, @RequestBody JsonNode body) {
        return tasks.update(user, taskId, PatchBody.of(body, TaskService.PATCH_FIELDS));
    }

    @DeleteMapping("/tasks/{taskId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long taskId) {
        tasks.delete(user, taskId);
        return ResponseEntity.noContent().build();
    }
}

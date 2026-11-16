package pt.matriz.projects;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import pt.matriz.projects.ProjectAccess.ProjectRole;
import pt.matriz.projects.ProjectViews.Member;
import pt.matriz.projects.ProjectViews.Project;
import pt.matriz.projects.ProjectViews.ProjectPage;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.web.PatchBody;

/** Projects and Members operations of openapi.yaml. HTTP only; the rules live in the services. */
@RestController
@RequestMapping("/projects")
class ProjectController {

    private final ProjectService projects;
    private final MemberService members;

    ProjectController(ProjectService projects, MemberService members) {
        this.projects = projects;
        this.members = members;
    }

    record ProjectCreateRequest(String name, String description) {
    }

    record MemberAddRequest(String email, ProjectRole projectRole) {
    }

    record MemberRoleRequest(ProjectRole projectRole) {
    }

    @GetMapping
    ProjectPage list(@AuthenticationPrincipal AuthenticatedUser user,
                     @RequestParam(required = false) String q,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return projects.list(user, q, page, size);
    }

    @PostMapping
    ResponseEntity<Project> create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestBody ProjectCreateRequest body) {
        Project created = projects.create(user, body.name(), body.description());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{projectId}")
    Project get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId) {
        return projects.get(user, projectId);
    }

    @PatchMapping("/{projectId}")
    Project update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                   @RequestBody JsonNode body) {
        return projects.update(user, projectId, PatchBody.of(body, Set.of("version", "name", "description")));
    }

    @DeleteMapping("/{projectId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId) {
        projects.delete(user, projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members")
    List<Member> listMembers(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId) {
        return members.list(user, projectId);
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    Member addMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                     @RequestBody MemberAddRequest body) {
        return members.add(user, projectId, body.email(), body.projectRole());
    }

    record AppointRequest(String email) {
    }

    @PostMapping("/{projectId}/managers")
    Member appointManager(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                          @RequestBody AppointRequest body) {
        return members.appointManager(user, projectId, body.email());
    }

    @PatchMapping("/{projectId}/members/{userId}")
    Member changeRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                      @PathVariable long userId, @RequestBody MemberRoleRequest body) {
        return members.changeRole(user, projectId, userId, body.projectRole());
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                      @PathVariable long userId) {
        members.remove(user, projectId, userId);
        return ResponseEntity.noContent().build();
    }
}

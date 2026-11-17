package pt.matriz.projects;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pt.matriz.accounts.UserSummary;
import pt.matriz.tasks.TaskCounts;

/** Response bodies of the Projects and Members operations (openapi.yaml schemas). */
public final class ProjectViews {

    private ProjectViews() {
    }

    /** ProjectSummary: `myRole` is null and `taskCounts` omitted for the Admin account. */
    public record ProjectSummary(long id, String name, String description, ProjectAccess.ProjectRole myRole,
                                 long memberCount,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) TaskCounts.Counts taskCounts) {
    }

    /** Project = ProjectSummary + creator, dates and version. */
    public record Project(long id, String name, String description, ProjectAccess.ProjectRole myRole,
                          long memberCount,
                          @JsonInclude(JsonInclude.Include.NON_NULL) TaskCounts.Counts taskCounts,
                          UserSummary createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, int version) {
    }

    /** ProjectPage: one page of projects and the number of matches over all pages. */
    public record ProjectPage(List<ProjectSummary> items, int page, int size, long totalItems) {
    }

    /** Member of a project. */
    public record Member(UserSummary user, ProjectAccess.ProjectRole projectRole, OffsetDateTime addedAt) {
    }
}

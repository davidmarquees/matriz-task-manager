package pt.matriz.tasks;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import pt.matriz.accounts.UserSummary;

/** Response bodies of the Tasks operations (openapi.yaml schemas Task, TaskLastChange, TaskPage). */
public final class TaskViews {

    private TaskViews() {
    }

    public enum Status { TODO, IN_PROGRESS, DONE }

    public enum Priority { LOW, MEDIUM, HIGH }

    public record Task(long id, long projectId, String title, String description, Status status, Priority priority,
                       LocalDate dueDate, List<UserSummary> assignees, UserSummary createdBy,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt, int version, LastChange lastChange) {
    }

    /**
     * Last change of a task (extra feature, option A): the date for every member; who and what only for
     * the project's Managers (A23, D41), otherwise omitted.
     */
    public record LastChange(OffsetDateTime changedAt,
                             @JsonInclude(JsonInclude.Include.NON_NULL) UserSummary changedBy,
                             @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldChange> changes) {
    }

    /** One changed field; `old` and `new` are null for description and assignees. */
    public record FieldChange(String field, String old, @com.fasterxml.jackson.annotation.JsonProperty("new") String newValue) {
    }

    public record TaskPage(List<Task> items, int page, int size, long totalItems) {
    }
}

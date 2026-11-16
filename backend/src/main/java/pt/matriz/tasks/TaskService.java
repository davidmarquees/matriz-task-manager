package pt.matriz.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.UserSummary;
import pt.matriz.audit.ActivityLog;
import pt.matriz.projects.ProjectAccess;
import pt.matriz.projects.ProjectAccess.ProjectRole;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.tasks.TaskViews.FieldChange;
import pt.matriz.tasks.TaskViews.LastChange;
import pt.matriz.tasks.TaskViews.Priority;
import pt.matriz.tasks.TaskViews.Status;
import pt.matriz.tasks.TaskViews.Task;
import pt.matriz.tasks.TaskViews.TaskPage;
import pt.matriz.web.ApiException;
import pt.matriz.web.PatchBody;
import pt.matriz.web.TextInput;

/**
 * Tasks of a project (FR5 to FR8, FR10). Any member creates, reads and edits tasks (A13); a project
 * Manager or the creator deletes them (A10, D23). Rules are checked on the server (NFR4) and every
 * change is written to the activity log in the same transaction (FR13).
 */
@Service
public class TaskService {

    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 150;
    static final int DESCRIPTION_MAX = 2000;
    static final int SEARCH_MAX = 150;
    static final int PAGE_SIZE_MAX = 100;
    static final Set<String> PATCH_FIELDS =
            Set.of("version", "title", "description", "status", "priority", "dueDate", "assigneeIds");

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ProjectAccess access;
    private final AccountService accounts;
    private final ActivityLog activity;
    private final ObjectMapper json;

    public TaskService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, ProjectAccess access,
                       AccountService accounts, ActivityLog activity, ObjectMapper json) {
        this.jdbc = jdbc;
        this.named = named;
        this.access = access;
        this.accounts = accounts;
        this.activity = activity;
        this.json = json;
    }

    /** Request body of createTask (contract TaskCreateRequest); text values are checked in the service. */
    public record NewTask(String title, String description, String status, String priority, String dueDate,
                          List<Long> assigneeIds) {
    }

    // ------------------------------------------------------------------ list

    /** Tasks of the project, newest first, with text search and filters combined with AND (FR10). */
    @Transactional(readOnly = true)
    public TaskPage list(AuthenticatedUser user, long projectId, String q, String status, String priority,
                         Long assigneeId, int page, int size) {
        ProjectRole role = requireProjectMember(user, projectId);
        if (page < 0) {
            throw ApiException.invalid("page", "INVALID_VALUE");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw ApiException.invalid("size", "INVALID_VALUE");
        }
        if (q != null && q.length() > SEARCH_MAX) {
            throw ApiException.invalid("q", "TOO_LONG");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("projectId", projectId);
        StringBuilder where = new StringBuilder(" WHERE t.project_id = :projectId");
        if (q != null && !q.isBlank()) {
            where.append(" AND (t.title ILIKE :q ESCAPE '\\' OR t.description ILIKE :q ESCAPE '\\')");
            params.addValue("q", "%" + escapeLike(q.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = :status");
            params.addValue("status", parseStatus(status).name());
        }
        if (priority != null && !priority.isBlank()) {
            where.append(" AND t.priority = :priority");
            params.addValue("priority", parsePriority(priority).name());
        }
        if (assigneeId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM task_assignees a WHERE a.task_id = t.id AND a.user_id = :assignee)");
            params.addValue("assignee", assigneeId);
        }
        long total = named.queryForObject("SELECT count(*) FROM tasks t" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<Row> rows = named.query("SELECT t.* FROM tasks t" + where
                + " ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset", params, (rs, i) -> Row.of(rs));
        return new TaskPage(toTasks(rows, role), page, size, total);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Task get(AuthenticatedUser user, long taskId) {
        Row row = findRow(taskId, false);
        ProjectRole role = requireProjectMember(user, row.projectId());
        return toTasks(List.of(row), role).get(0);
    }

    // ------------------------------------------------------------------ create

    /** Any member creates a task; the creator comes from the session; defaults TODO and MEDIUM (A5). */
    @Transactional
    public Task create(AuthenticatedUser user, long projectId, NewTask body) {
        ProjectRole role = requireProjectMember(user, projectId);
        String title = TextInput.required(body.title(), "title", TITLE_MIN, TITLE_MAX);
        String description = TextInput.optional(body.description(), "description", DESCRIPTION_MAX);
        Status status = body.status() == null ? Status.TODO : parseStatus(body.status());
        Priority priority = body.priority() == null ? Priority.MEDIUM : parsePriority(body.priority());
        LocalDate dueDate = parseDate(body.dueDate());
        Set<Long> assignees = checkAssignees(projectId, body.assigneeIds());

        ProjectAccess.LockedProject project = access.lock(projectId); // D26: project deleted meanwhile → 404
        long id = jdbc.queryForObject("INSERT INTO tasks (project_id, title, description, status, priority, due_date, "
                        + "created_by) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                projectId, title, description, status.name(), priority.name(), dueDate, user.id());
        for (long assignee : assignees) {
            jdbc.update("INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", id, projectId, assignee);
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("title", ActivityLog.change(null, title));
        changes.put("status", ActivityLog.change(null, status.name()));
        changes.put("priority", ActivityLog.change(null, priority.name()));
        if (description != null) changes.put("description", ActivityLog.change(null, description));
        if (dueDate != null) changes.put("dueDate", ActivityLog.change(null, dueDate.toString()));
        if (!assignees.isEmpty()) changes.put("assignees", ActivityLog.change(null, List.copyOf(assignees)));
        activity.record(projectId, project.name(), user.id(), ActivityLog.EntityType.TASK, id,
                ActivityLog.Action.CREATED, changes);
        recordAssignees(projectId, project.name(), user.id(), id, assignees, ActivityLog.Action.ADDED);
        return toTasks(List.of(findRow(id, false)), role).get(0);
    }

    // ------------------------------------------------------------------ update

    /**
     * Partial update by any member (A13). Stale version → 409 (D25); no real change → no new version and
     * no activity entry (D43). `projectId` is not an accepted field: a task cannot move (D27, 400).
     */
    @Transactional
    public Task update(AuthenticatedUser user, long taskId, PatchBody patch) {
        Row current = findRow(taskId, false);
        ProjectRole role = requireProjectMember(user, current.projectId());
        int version = patch.version();

        String title = patch.has("title") ? TextInput.required(patch.text("title"), "title", TITLE_MIN, TITLE_MAX)
                : current.title();
        String description = patch.has("description")
                ? TextInput.optional(patch.text("description"), "description", DESCRIPTION_MAX) : current.description();
        Status status = patch.has("status") ? parseStatus(requiredText(patch, "status")) : current.status();
        Priority priority = patch.has("priority") ? parsePriority(requiredText(patch, "priority")) : current.priority();
        LocalDate dueDate = patch.has("dueDate") ? parseDate(patch.text("dueDate")) : current.dueDate();
        Set<Long> newAssignees = patch.has("assigneeIds")
                ? checkAssignees(current.projectId(), assigneeIds(patch.node("assigneeIds"))) : null;

        ProjectAccess.LockedProject project = access.lock(current.projectId());
        Row locked = findRow(taskId, true);
        if (locked.version() != version) {
            throw ApiException.conflict("VERSION_CONFLICT");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!title.equals(locked.title())) changes.put("title", ActivityLog.change(locked.title(), title));
        if (!Objects.equals(description, locked.description()))
            changes.put("description", ActivityLog.change(locked.description(), description));
        if (status != locked.status()) changes.put("status", ActivityLog.change(locked.status().name(), status.name()));
        if (priority != locked.priority())
            changes.put("priority", ActivityLog.change(locked.priority().name(), priority.name()));
        if (!Objects.equals(dueDate, locked.dueDate()))
            changes.put("dueDate", ActivityLog.change(str(locked.dueDate()), str(dueDate)));

        Set<Long> added = Set.of();
        Set<Long> removed = Set.of();
        if (newAssignees != null) {
            Set<Long> currentAssignees = new LinkedHashSet<>(assigneesOf(List.of(taskId)).getOrDefault(taskId, List.of()));
            added = new LinkedHashSet<>(newAssignees);
            added.removeAll(currentAssignees);
            removed = new LinkedHashSet<>(currentAssignees);
            removed.removeAll(newAssignees);
            if (!added.isEmpty() || !removed.isEmpty()) {
                changes.put("assignees", ActivityLog.change(List.copyOf(currentAssignees), List.copyOf(newAssignees)));
            }
        }

        if (!changes.isEmpty()) {
            jdbc.update("UPDATE tasks SET title = ?, description = ?, status = ?, priority = ?, due_date = ?, "
                            + "version = version + 1, updated_at = now() WHERE id = ?",
                    title, description, status.name(), priority.name(), dueDate, taskId);
            for (long userId : removed) {
                jdbc.update("DELETE FROM task_assignees WHERE task_id = ? AND user_id = ?", taskId, userId);
            }
            for (long userId : added) {
                jdbc.update("INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)",
                        taskId, locked.projectId(), userId);
            }
            activity.record(locked.projectId(), project.name(), user.id(), ActivityLog.EntityType.TASK, taskId,
                    ActivityLog.Action.UPDATED, changes);
            recordAssignees(locked.projectId(), project.name(), user.id(), taskId, added, ActivityLog.Action.ADDED);
            recordAssignees(locked.projectId(), project.name(), user.id(), taskId, removed, ActivityLog.Action.REMOVED);
        }
        return toTasks(List.of(findRow(taskId, false)), role).get(0);
    }

    // ------------------------------------------------------------------ delete

    /** A project Manager or the task's creator (A10, D23); deletes its assignees and comments (A11). */
    @Transactional
    public void delete(AuthenticatedUser user, long taskId) {
        Row current = findRow(taskId, false);
        ProjectRole role = requireProjectMember(user, current.projectId());
        if (role != ProjectRole.MANAGER && current.createdBy() != user.id()) {
            throw ApiException.forbidden();
        }
        ProjectAccess.LockedProject project = access.lock(current.projectId());
        Row locked = findRow(taskId, true);
        jdbc.update("DELETE FROM tasks WHERE id = ?", taskId);
        // the deleted task keeps its title in the log, so the entry stays readable
        activity.record(locked.projectId(), project.name(), user.id(), ActivityLog.EntityType.TASK, taskId,
                ActivityLog.Action.DELETED, Map.of("title", ActivityLog.change(locked.title(), null)));
    }

    // ------------------------------------------------------------------ helpers

    /** Members only; the Admin account gets 403 on an existing project (D11), everyone else 404 (E4). */
    private ProjectRole requireProjectMember(AuthenticatedUser user, long projectId) {
        if (ProjectAccess.isAdmin(user)) {
            access.requireReader(projectId, user); // 404 when the project does not exist
            throw ApiException.forbidden();
        }
        return access.requireMember(projectId, user.id());
    }

    private Row findRow(long taskId, boolean forUpdate) {
        List<Row> rows = jdbc.query("SELECT * FROM tasks WHERE id = ?" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, i) -> Row.of(rs), taskId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    /** Assignees must be active members of the project, without duplicates (FR8, D21, D22). */
    private Set<Long> checkAssignees(long projectId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id < 1) {
                throw ApiException.invalid("assigneeIds", "INVALID_VALUE");
            }
            if (!unique.add(id)) {
                throw ApiException.invalid("assigneeIds", "DUPLICATE");
            }
        }
        Set<Long> members = new HashSet<>(named.queryForList(
                "SELECT user_id FROM project_members WHERE project_id = :projectId AND user_id IN (:ids)",
                new MapSqlParameterSource("projectId", projectId).addValue("ids", unique), Long.class));
        Map<Long, UserSummary> users = accounts.summaries(members);
        for (Long id : unique) {
            if (!members.contains(id) || users.get(id) == null || !users.get(id).active()) {
                throw ApiException.invalid("assigneeIds", "NOT_A_PROJECT_MEMBER");
            }
        }
        return unique;
    }

    /** Assignee changes are also written as ASSIGNEE entries (entity = user), linked to the task. */
    private void recordAssignees(long projectId, String projectName, long actorId, long taskId, Collection<Long> userIds,
                                 ActivityLog.Action action) {
        for (long userId : userIds) {
            activity.record(projectId, projectName, actorId, ActivityLog.EntityType.ASSIGNEE, userId, action,
                    Map.of("taskId", taskId));
        }
    }

    private List<Task> toTasks(List<Row> rows, ProjectRole role) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Row::id).toList();
        Map<Long, List<Long>> assignees = assigneesOf(ids);
        Map<Long, LastChangeRow> lastChanges = lastChangesOf(rows.get(0).projectId(), ids);

        Set<Long> userIds = new HashSet<>();
        rows.forEach(r -> userIds.add(r.createdBy()));
        assignees.values().forEach(userIds::addAll);
        lastChanges.values().forEach(c -> userIds.add(c.actorId()));
        Map<Long, UserSummary> users = accounts.summaries(userIds);

        boolean manager = role == ProjectRole.MANAGER;
        List<Task> tasks = new ArrayList<>();
        for (Row r : rows) {
            List<UserSummary> taskAssignees = assignees.getOrDefault(r.id(), List.of()).stream()
                    .map(users::get).filter(Objects::nonNull)
                    .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.name()), String.valueOf(b.name())))
                    .toList();
            LastChangeRow change = lastChanges.get(r.id());
            LastChange lastChange = change == null ? null : new LastChange(change.changedAt(),
                    manager ? users.get(change.actorId()) : null,
                    manager ? change.fields() : null);
            tasks.add(new Task(r.id(), r.projectId(), r.title(), r.description(), r.status(), r.priority(), r.dueDate(),
                    taskAssignees, users.get(r.createdBy()), r.createdAt(), r.updatedAt(), r.version(), lastChange));
        }
        return tasks;
    }

    private Map<Long, List<Long>> assigneesOf(Collection<Long> taskIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        named.query("SELECT task_id, user_id FROM task_assignees WHERE task_id IN (:ids) ORDER BY assigned_at, user_id",
                Map.of("ids", taskIds),
                rs -> {
                    result.computeIfAbsent(rs.getLong("task_id"), k -> new ArrayList<>()).add(rs.getLong("user_id"));
                });
        return result;
    }

    /** Latest UPDATED entry of each task in the activity log (extra feature "last change"). */
    private Map<Long, LastChangeRow> lastChangesOf(long projectId, Collection<Long> taskIds) {
        Map<Long, LastChangeRow> result = new HashMap<>();
        named.query("SELECT DISTINCT ON (entity_id) entity_id, actor_id, changes::text AS changes, created_at "
                        + "FROM activity_log WHERE project_id = :projectId AND entity_type = 'TASK' AND action = 'UPDATED' "
                        + "AND entity_id IN (:ids) ORDER BY entity_id, created_at DESC, id DESC",
                new MapSqlParameterSource("projectId", projectId).addValue("ids", taskIds),
                rs -> {
                    result.put(rs.getLong("entity_id"), new LastChangeRow(rs.getLong("actor_id"),
                            rs.getObject("created_at", OffsetDateTime.class), fields(rs.getString("changes"))));
                });
        return result;
    }

    private List<FieldChange> fields(String changesJson) {
        if (changesJson == null) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(changesJson);
            List<FieldChange> fields = new ArrayList<>();
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                boolean hideValues = field.equals("description") || field.equals("assignees");
                fields.add(new FieldChange(field,
                        hideValues ? null : text(entry.getValue().get("old")),
                        hideValues ? null : text(entry.getValue().get("new"))));
            });
            return fields;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unreadable activity entry", e);
        }
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String requiredText(PatchBody patch, String field) {
        String value = patch.text(field);
        if (value == null) {
            throw ApiException.invalid(field, "REQUIRED");
        }
        return value;
    }

    private static List<Long> assigneeIds(JsonNode node) {
        if (node == null || node.isNull()) {
            throw ApiException.invalid("assigneeIds", "REQUIRED");
        }
        if (!node.isArray()) {
            throw ApiException.invalid("assigneeIds", "INVALID_VALUE");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber()) {
                throw ApiException.invalid("assigneeIds", "INVALID_VALUE");
            }
            ids.add(item.longValue());
        }
        return ids;
    }

    static Status parseStatus(String value) {
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("status", "INVALID_VALUE");
        }
    }

    static Priority parsePriority(String value) {
        try {
            return Priority.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("priority", "INVALID_VALUE");
        }
    }

    /** Optional date, any date accepted (A19); a malformed one is 422 INVALID_DATE. */
    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("dueDate", "INVALID_DATE");
        }
    }

    private static String str(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record LastChangeRow(long actorId, OffsetDateTime changedAt, List<FieldChange> fields) {
    }

    private record Row(long id, long projectId, String title, String description, Status status, Priority priority,
                       LocalDate dueDate, long createdBy, int version, OffsetDateTime createdAt,
                       OffsetDateTime updatedAt) {
        static Row of(ResultSet rs) throws SQLException {
            return new Row(rs.getLong("id"), rs.getLong("project_id"), rs.getString("title"),
                    rs.getString("description"), Status.valueOf(rs.getString("status")),
                    Priority.valueOf(rs.getString("priority")), rs.getObject("due_date", LocalDate.class),
                    rs.getLong("created_by"), rs.getInt("version"), rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class));
        }
    }
}

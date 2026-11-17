package pt.matriz.audit;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.UserSummary;
import pt.matriz.projects.ProjectAccess;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.web.ApiException;

/**
 * Reading a project's activity log (FR13, US12): only the project's Managers (A23, D41). Read-only;
 * nobody can change or delete an entry (D39).
 */
@Service
public class ActivityQuery {

    static final int PAGE_SIZE_MAX = 100;

    public record Entry(long id, UserSummary actor, String entityType, long entityId, String action,
                        @JsonInclude(JsonInclude.Include.NON_NULL) UserSummary entityUser, String entityTitle,
                        JsonNode changes, OffsetDateTime createdAt) {
    }

    public record EntryPage(List<Entry> items, int page, int size, long totalItems) {
    }

    private record Row(long id, long actorId, String entityType, long entityId, String action, String changes,
                       OffsetDateTime createdAt, String projectName, String currentTaskTitle) {
    }

    private final JdbcTemplate jdbc;
    private final ProjectAccess access;
    private final AccountService accounts;
    private final ObjectMapper json;

    public ActivityQuery(JdbcTemplate jdbc, ProjectAccess access, AccountService accounts, ObjectMapper json) {
        this.jdbc = jdbc;
        this.access = access;
        this.accounts = accounts;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public EntryPage list(AuthenticatedUser user, long projectId, int page, int size) {
        access.requireManager(projectId, user); // Admin 403 (D11), Member 403 (D41), non-member 404 (D42)
        if (page < 0) {
            throw ApiException.invalid("page", "INVALID_VALUE");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw ApiException.invalid("size", "INVALID_VALUE");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ?", Long.class, projectId);
        // Current task title for entries about a task (task itself, its assignees or comments).
        List<Row> rows = jdbc.query("""
                SELECT a.id, a.actor_id, a.entity_type, a.entity_id, a.action, a.changes::text AS changes,
                       a.created_at, a.project_name, t.title AS task_title
                FROM activity_log a
                LEFT JOIN tasks t ON t.id = CASE
                    WHEN a.entity_type = 'TASK' THEN a.entity_id
                    WHEN a.entity_type IN ('ASSIGNEE', 'COMMENT') THEN (a.changes ->> 'taskId')::bigint
                END
                WHERE a.project_id = ?
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?""",
                (rs, i) -> new Row(rs.getLong("id"), rs.getLong("actor_id"), rs.getString("entity_type"),
                        rs.getLong("entity_id"), rs.getString("action"), rs.getString("changes"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getString("project_name"),
                        rs.getString("task_title")),
                projectId, size, (long) page * size);

        Set<Long> userIds = new HashSet<>();
        for (Row r : rows) {
            userIds.add(r.actorId());
            if (r.entityType().equals("MEMBER") || r.entityType().equals("ASSIGNEE")) {
                userIds.add(r.entityId());
            }
        }
        Map<Long, UserSummary> users = accounts.summaries(userIds);
        List<Entry> items = rows.stream().map(r -> {
            JsonNode changes = parse(r.changes());
            boolean aboutUser = r.entityType().equals("MEMBER") || r.entityType().equals("ASSIGNEE");
            return new Entry(r.id(), users.get(r.actorId()), r.entityType(), r.entityId(), r.action(),
                    aboutUser ? users.get(r.entityId()) : null, title(r, changes), changes, r.createdAt());
        }).toList();
        return new EntryPage(items, page, size, total);
    }

    /** Project name, the current task title, or the title kept in the entry for a deleted task. */
    private static String title(Row r, JsonNode changes) {
        if (r.entityType().equals("PROJECT") || r.entityType().equals("MEMBER")) {
            return r.projectName();
        }
        if (r.currentTaskTitle() != null) {
            return r.currentTaskTitle();
        }
        JsonNode title = changes == null ? null : changes.get("title");
        if (title != null) {
            JsonNode old = title.get("old");
            JsonNode created = title.get("new");
            if (old != null && !old.isNull()) return old.asText();
            if (created != null && !created.isNull()) return created.asText();
        }
        return null;
    }

    private JsonNode parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable activity entry", e);
        }
    }
}

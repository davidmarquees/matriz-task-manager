package pt.matriz.comments;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.UserSummary;
import pt.matriz.audit.ActivityLog;
import pt.matriz.projects.ProjectAccess;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.tasks.TaskService;
import pt.matriz.web.ApiException;
import pt.matriz.web.TextInput;

/**
 * Task comments (FR11, US7): any member of the task's project lists and adds comments. Author and date
 * are set here, never taken from the request. Comments cannot be edited or deleted (A14).
 */
@Service
public class CommentService {

    static final int BODY_MAX = 2000;

    /** Comment schema of openapi.yaml. */
    public record Comment(long id, long taskId, UserSummary author, String body, OffsetDateTime createdAt) {
    }

    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final ProjectAccess access;
    private final AccountService accounts;
    private final ActivityLog activity;

    public CommentService(JdbcTemplate jdbc, TaskService tasks, ProjectAccess access, AccountService accounts,
                          ActivityLog activity) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.access = access;
        this.accounts = accounts;
        this.activity = activity;
    }

    /** Oldest first; not paginated (openapi.yaml assumption). */
    @Transactional(readOnly = true)
    public List<Comment> list(AuthenticatedUser user, long taskId) {
        tasks.requireTaskAccess(user, taskId);
        List<Row> rows = jdbc.query("SELECT id, task_id, author_id, body, created_at FROM comments WHERE task_id = ? "
                + "ORDER BY created_at, id", (rs, i) -> new Row(rs.getLong("id"), rs.getLong("task_id"),
                rs.getLong("author_id"), rs.getString("body"), rs.getObject("created_at", OffsetDateTime.class)), taskId);
        Map<Long, UserSummary> authors = accounts.summaries(rows.stream().map(Row::authorId).distinct().toList());
        return rows.stream().map(r -> new Comment(r.id(), r.taskId(), authors.get(r.authorId()), r.body(), r.createdAt()))
                .toList();
    }

    /** 1 to 2,000 characters after trimming, not only spaces (D20), no control characters except tab and line breaks (D49); written to the activity log without the text. */
    @Transactional
    public Comment add(AuthenticatedUser user, long taskId, String body) {
        long projectId = tasks.requireTaskAccess(user, taskId);
        String text = TextInput.requiredMultiLine(body, "body", 1, BODY_MAX);
        ProjectAccess.LockedProject project = access.lock(projectId);
        Integer stillThere = jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id = ?", Integer.class, taskId);
        if (stillThere == null || stillThere == 0) {
            throw ApiException.notFound(); // D26
        }
        Row row = jdbc.queryForObject("INSERT INTO comments (task_id, author_id, body) VALUES (?, ?, ?) "
                        + "RETURNING id, task_id, author_id, body, created_at",
                (rs, i) -> new Row(rs.getLong("id"), rs.getLong("task_id"), rs.getLong("author_id"),
                        rs.getString("body"), rs.getObject("created_at", OffsetDateTime.class)),
                taskId, user.id(), text);
        activity.record(projectId, project.name(), user.id(), ActivityLog.EntityType.COMMENT, row.id(),
                ActivityLog.Action.CREATED, Map.of("taskId", taskId));
        return new Comment(row.id(), row.taskId(), accounts.summaries(List.of(user.id())).get(user.id()), row.body(),
                row.createdAt());
    }

    private record Row(long id, long taskId, long authorId, String body, OffsetDateTime createdAt) {
    }
}

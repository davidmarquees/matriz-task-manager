package pt.matriz.projects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.UserSummary;
import pt.matriz.audit.ActivityLog;
import pt.matriz.projects.ProjectAccess.ProjectRole;
import pt.matriz.projects.ProjectViews.Project;
import pt.matriz.projects.ProjectViews.ProjectPage;
import pt.matriz.projects.ProjectViews.ProjectSummary;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.tasks.TaskCounts;
import pt.matriz.web.ApiException;
import pt.matriz.web.PatchBody;
import pt.matriz.web.TextInput;

/**
 * Projects (FR2, FR4, FR10): create, list and search, read, edit and delete. Every rule is checked here,
 * on the server (NFR4); identity comes from the session, never from the request body.
 */
@Service
public class ProjectService {

    static final int NAME_MAX = 100;
    static final int DESCRIPTION_MAX = 2000;
    static final int SEARCH_MAX = 100;
    static final int PAGE_SIZE_MAX = 100;

    private final JdbcTemplate jdbc;
    private final ProjectAccess access;
    private final AccountService accounts;
    private final TaskCounts taskCounts;
    private final ActivityLog activity;
    private final MemberService members;

    public ProjectService(JdbcTemplate jdbc, ProjectAccess access, AccountService accounts, TaskCounts taskCounts,
                          ActivityLog activity, MemberService members) {
        this.jdbc = jdbc;
        this.access = access;
        this.accounts = accounts;
        this.taskCounts = taskCounts;
        this.activity = activity;
        this.members = members;
    }

    /** Projects visible to the user, sorted by name; the Admin account sees every project (without counts). */
    @Transactional(readOnly = true)
    public ProjectPage list(AuthenticatedUser user, String q, int page, int size) {
        if (page < 0) {
            throw ApiException.invalid("page", "INVALID_VALUE");
        }
        if (size < 1 || size > PAGE_SIZE_MAX) {
            throw ApiException.invalid("size", "INVALID_VALUE");
        }
        if (q != null && q.length() > SEARCH_MAX) {
            throw ApiException.invalid("q", "TOO_LONG");
        }
        String pattern = (q == null || q.isBlank()) ? null : "%" + escapeLike(q.trim()) + "%";
        boolean admin = ProjectAccess.isAdmin(user);

        String from = admin
                ? " FROM projects p LEFT JOIN project_members m ON m.project_id = p.id AND m.user_id = ?"
                : " FROM projects p JOIN project_members m ON m.project_id = p.id AND m.user_id = ?";
        String where = pattern == null ? "" : " WHERE p.name ILIKE ? ESCAPE '\\'";
        Object[] filter = pattern == null ? new Object[] {user.id()} : new Object[] {user.id(), pattern};

        long total = jdbc.queryForObject("SELECT count(*)" + from + where, Long.class, filter);
        List<Object> params = new ArrayList<>(List.of(filter));
        params.add(size);
        params.add((long) page * size);
        List<ProjectSummary> items = jdbc.query(
                "SELECT p.id, p.name, p.description, m.project_role, "
                        + "(SELECT count(*) FROM project_members x WHERE x.project_id = p.id) AS member_count"
                        + from + where + " ORDER BY lower(p.name), p.id LIMIT ? OFFSET ?",
                (rs, i) -> new ProjectSummary(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        role(rs), rs.getLong("member_count"), null),
                params.toArray());

        if (!admin && !items.isEmpty()) {
            Map<Long, TaskCounts.Counts> counts = taskCounts.byProject(items.stream().map(ProjectSummary::id).toList());
            items = items.stream().map(p -> new ProjectSummary(p.id(), p.name(), p.description(), p.myRole(),
                    p.memberCount(), counts.get(p.id()))).toList();
        }
        return new ProjectPage(items, page, size, total);
    }

    /** Only Manager accounts create projects; the creator becomes the first Manager (FR2, A1, A12, D9). */
    @Transactional
    public Project create(AuthenticatedUser user, String name, String description) {
        if (!"MANAGER".equals(user.accountRole())) {
            throw ApiException.forbidden();
        }
        String cleanName = TextInput.required(name, "name", 1, NAME_MAX);
        String cleanDescription = TextInput.optional(description, "description", DESCRIPTION_MAX);

        long id = jdbc.queryForObject(
                "INSERT INTO projects (name, description, created_by) VALUES (?, ?, ?) RETURNING id",
                Long.class, cleanName, cleanDescription, user.id());
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("name", ActivityLog.change(null, cleanName));
        if (cleanDescription != null) {
            changes.put("description", ActivityLog.change(null, cleanDescription));
        }
        activity.record(id, cleanName, user.id(), ActivityLog.EntityType.PROJECT, id, ActivityLog.Action.CREATED, changes);
        members.addCreator(id, cleanName, user.id());
        return get(user, id);
    }

    /** Members of the project and the Admin account can read it (FR4, E4). */
    @Transactional(readOnly = true)
    public Project get(AuthenticatedUser user, long projectId) {
        ProjectRole role = access.requireReader(projectId, user);
        Row row = jdbc.queryForObject("SELECT id, name, description, created_by, version, created_at, updated_at, "
                        + "(SELECT count(*) FROM project_members x WHERE x.project_id = p.id) AS member_count "
                        + "FROM projects p WHERE id = ?",
                (rs, i) -> Row.of(rs), projectId);
        UserSummary creator = accounts.summaries(List.of(row.createdBy())).get(row.createdBy());
        TaskCounts.Counts counts = ProjectAccess.isAdmin(user) ? null
                : taskCounts.byProject(List.of(projectId)).get(projectId);
        return new Project(row.id(), row.name(), row.description(), role, row.memberCount(), counts, creator,
                row.createdAt(), row.updatedAt(), row.version());
    }

    /**
     * Partial update by a project Manager (FR2, D10). Stale `version` → 409 (D25); no real change → saved
     * without a new version and without an activity entry (D43).
     */
    @Transactional
    public Project update(AuthenticatedUser user, long projectId, PatchBody patch) {
        access.requireManager(projectId, user);
        int version = patch.version();
        String newName = null;
        if (patch.has("name")) {
            newName = TextInput.required(patch.text("name"), "name", 1, NAME_MAX);
        }
        String newDescription = patch.has("description")
                ? TextInput.optional(patch.text("description"), "description", DESCRIPTION_MAX) : null;

        ProjectAccess.LockedProject current = access.lock(projectId);
        if (current.version() != version) {
            throw ApiException.conflict("VERSION_CONFLICT");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        String name = current.name();
        String description = current.description();
        if (newName != null && !newName.equals(current.name())) {
            changes.put("name", ActivityLog.change(current.name(), newName));
            name = newName;
        }
        if (patch.has("description") && !java.util.Objects.equals(newDescription, current.description())) {
            changes.put("description", ActivityLog.change(current.description(), newDescription));
            description = newDescription;
        }
        if (!changes.isEmpty()) {
            jdbc.update("UPDATE projects SET name = ?, description = ?, version = version + 1, updated_at = now() "
                    + "WHERE id = ?", name, description, projectId);
            activity.record(projectId, name, user.id(), ActivityLog.EntityType.PROJECT, projectId,
                    ActivityLog.Action.UPDATED, changes);
        }
        return get(user, projectId);
    }

    /**
     * Delete by a project Manager (A10, D10). In one transaction (D29) the database cascades delete its
     * members, tasks, assignees and comments (A11); the activity log keeps the record.
     */
    @Transactional
    public void delete(AuthenticatedUser user, long projectId) {
        access.requireManager(projectId, user);
        ProjectAccess.LockedProject current = access.lock(projectId);
        members.recordRemovalOfAll(projectId, current.name(), user.id());
        jdbc.update("DELETE FROM projects WHERE id = ?", projectId);
        activity.record(projectId, current.name(), user.id(), ActivityLog.EntityType.PROJECT, projectId,
                ActivityLog.Action.DELETED, Map.of("name", ActivityLog.change(current.name(), null)));
    }

    private static ProjectRole role(ResultSet rs) throws SQLException {
        String role = rs.getString("project_role");
        return role == null ? null : ProjectRole.valueOf(role);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    record Row(long id, String name, String description, long createdBy, int version, OffsetDateTime createdAt,
               OffsetDateTime updatedAt, long memberCount) {
        static Row of(ResultSet rs) throws SQLException {
            return new Row(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                    rs.getLong("created_by"), rs.getInt("version"),
                    rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getLong("member_count"));
        }
    }
}

package pt.matriz.projects;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.AccountView;
import pt.matriz.accounts.User;
import pt.matriz.accounts.UserSummary;
import pt.matriz.audit.ActivityLog;
import pt.matriz.audit.UserAuditLog;
import pt.matriz.projects.ProjectAccess.ProjectRole;
import pt.matriz.projects.ProjectViews.Member;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.web.ApiException;

/**
 * Project members and project roles (FR3, A1). A project always keeps at least one Manager (E6, D16).
 * Every change is written to both logs: the project's activity log and the user's audit log.
 */
@Service
public class MemberService {

    private final JdbcTemplate jdbc;
    private final ProjectAccess access;
    private final AccountService accounts;
    private final ActivityLog activity;
    private final UserAuditLog audit;

    public MemberService(JdbcTemplate jdbc, ProjectAccess access, AccountService accounts, ActivityLog activity,
                         UserAuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.accounts = accounts;
        this.activity = activity;
        this.audit = audit;
    }

    /** Active members sorted by name; also the users eligible as assignees (FR8). Members and the Admin. */
    @Transactional(readOnly = true)
    public List<Member> list(AuthenticatedUser user, long projectId) {
        access.requireReader(projectId, user);
        List<Row> rows = jdbc.query(
                "SELECT user_id, project_role, added_at FROM project_members WHERE project_id = ?",
                (rs, i) -> new Row(rs.getLong("user_id"), ProjectRole.valueOf(rs.getString("project_role")),
                        rs.getObject("added_at", OffsetDateTime.class)),
                projectId);
        Map<Long, UserSummary> users = accounts.summaries(rows.stream().map(Row::userId).toList());
        return rows.stream()
                .map(r -> new Member(users.get(r.userId()), r.role(), r.addedAt()))
                .filter(m -> m.user() != null && m.user().active())
                .sorted(Comparator.comparing((Member m) -> m.user().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(m -> m.user().id()))
                .toList();
    }

    /**
     * Adds a user by email (Managers only). The account must be active and not the Admin (D14);
     * already a member → 409 (D13); a removed member can be added again (D15).
     */
    @Transactional
    public Member add(AuthenticatedUser user, long projectId, String email, ProjectRole role) {
        access.requireManager(projectId, user);
        if (email == null || email.isBlank()) {
            throw ApiException.invalid("email", email == null ? "REQUIRED" : "BLANK");
        }
        ProjectRole newRole = role == null ? ProjectRole.MEMBER : role;
        AccountView account = accounts.findByEmail(email)
                .filter(a -> a.active() && a.accountRole() != User.AccountRole.ADMIN)
                .orElseThrow(() -> ApiException.invalid("email", "USER_NOT_ELIGIBLE"));

        ProjectAccess.LockedProject project = access.lock(projectId);
        try {
            jdbc.update("INSERT INTO project_members (project_id, user_id, project_role) VALUES (?, ?, ?)",
                    projectId, account.id(), newRole.name());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("ALREADY_MEMBER");
        }
        recordAdded(project.id(), project.name(), user.id(), account.id(), newRole);
        return member(projectId, account.id());
    }

    /** Promote or demote (Managers only); demoting the last Manager → 409 (E6, D16). */
    @Transactional
    public Member changeRole(AuthenticatedUser user, long projectId, long memberId, ProjectRole newRole) {
        access.requireManager(projectId, user);
        if (newRole == null) {
            throw ApiException.invalid("projectRole", "REQUIRED");
        }
        ProjectAccess.LockedProject project = access.lock(projectId);
        ProjectRole current = currentRole(projectId, memberId);
        if (current != newRole) {
            if (current == ProjectRole.MANAGER && managerCount(projectId) == 1) {
                throw ApiException.conflict("LAST_MANAGER");
            }
            jdbc.update("UPDATE project_members SET project_role = ? WHERE project_id = ? AND user_id = ?",
                    newRole.name(), projectId, memberId);
            activity.record(projectId, project.name(), user.id(), ActivityLog.EntityType.MEMBER, memberId,
                    ActivityLog.Action.UPDATED, Map.of("projectRole", ActivityLog.change(current.name(), newRole.name())));
            audit.record(user.id(), memberId, "PROJECT_ROLE_CHANGED", projectId, project.name(),
                    Map.of("projectRole", current.name()), Map.of("projectRole", newRole.name()));
        }
        return member(projectId, memberId);
    }

    /**
     * Removes a member (Managers only); removing the last Manager → 409 (E6, D16). Their assignments in
     * the project are removed by the database; the tasks stay (E1).
     */
    @Transactional
    public void remove(AuthenticatedUser user, long projectId, long memberId) {
        access.requireManager(projectId, user);
        ProjectAccess.LockedProject project = access.lock(projectId);
        ProjectRole current = currentRole(projectId, memberId);
        if (current == ProjectRole.MANAGER && managerCount(projectId) == 1) {
            throw ApiException.conflict("LAST_MANAGER");
        }
        jdbc.update("DELETE FROM project_members WHERE project_id = ? AND user_id = ?", projectId, memberId);
        recordRemoved(projectId, project.name(), user.id(), memberId, current);
    }

    /** The creator of a new project becomes its first Manager (A12). */
    @Transactional(propagation = Propagation.MANDATORY)
    void addCreator(long projectId, String projectName, long creatorId) {
        jdbc.update("INSERT INTO project_members (project_id, user_id, project_role) VALUES (?, ?, 'MANAGER')",
                projectId, creatorId);
        recordAdded(projectId, projectName, creatorId, creatorId, ProjectRole.MANAGER);
    }

    /** Before a project delete: one audit entry per membership (docs/database.md, user_audit_log). */
    @Transactional(propagation = Propagation.MANDATORY)
    void recordRemovalOfAll(long projectId, String projectName, long actorId) {
        jdbc.query("SELECT user_id, project_role FROM project_members WHERE project_id = ?",
                rs -> {
                    audit.record(actorId, rs.getLong("user_id"), "MEMBER_REMOVED", projectId, projectName,
                            Map.of("projectRole", rs.getString("project_role")), null);
                },
                projectId);
    }

    private void recordAdded(long projectId, String projectName, long actorId, long memberId, ProjectRole role) {
        activity.record(projectId, projectName, actorId, ActivityLog.EntityType.MEMBER, memberId,
                ActivityLog.Action.ADDED, Map.of("projectRole", ActivityLog.change(null, role.name())));
        audit.record(actorId, memberId, "MEMBER_ADDED", projectId, projectName, null,
                Map.of("projectRole", role.name()));
    }

    private void recordRemoved(long projectId, String projectName, long actorId, long memberId, ProjectRole role) {
        activity.record(projectId, projectName, actorId, ActivityLog.EntityType.MEMBER, memberId,
                ActivityLog.Action.REMOVED, Map.of("projectRole", ActivityLog.change(role.name(), null)));
        audit.record(actorId, memberId, "MEMBER_REMOVED", projectId, projectName,
                Map.of("projectRole", role.name()), null);
    }

    private ProjectRole currentRole(long projectId, long memberId) {
        List<String> roles = jdbc.queryForList(
                "SELECT project_role FROM project_members WHERE project_id = ? AND user_id = ?",
                String.class, projectId, memberId);
        if (roles.isEmpty()) {
            throw ApiException.notFound();
        }
        return ProjectRole.valueOf(roles.get(0));
    }

    private long managerCount(long projectId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM project_members WHERE project_id = ? AND project_role = 'MANAGER'",
                Long.class, projectId);
    }

    private Member member(long projectId, long userId) {
        Row row = jdbc.queryForObject(
                "SELECT user_id, project_role, added_at FROM project_members WHERE project_id = ? AND user_id = ?",
                (rs, i) -> new Row(rs.getLong("user_id"), ProjectRole.valueOf(rs.getString("project_role")),
                        rs.getObject("added_at", OffsetDateTime.class)),
                projectId, userId);
        return new Member(accounts.summaries(List.of(userId)).get(userId), row.role(), row.addedAt());
    }

    private record Row(long userId, ProjectRole role, OffsetDateTime addedAt) {
    }
}

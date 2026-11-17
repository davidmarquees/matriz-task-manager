package pt.matriz.projects;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.security.AuthenticatedUser;
import pt.matriz.web.ApiException;

/**
 * Access rules on a project (docs/implementation-plan.md section 4), checked on every request from
 * the database, never cached in the session (D17, D18). Used for every operation on a project and on
 * its members, tasks, comments and assignees.
 */
@Service
public class ProjectAccess {

    public enum ProjectRole { MANAGER, MEMBER }

    /** Locked project row: name and version, for the rest of the transaction. */
    public record LockedProject(long id, String name, String description, int version) {
    }

    private final JdbcTemplate jdbc;

    public ProjectAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The user's role in the project. 404 when the project does not exist or the user is not a member,
     * so its existence is not revealed (E4).
     */
    @Transactional(readOnly = true)
    public ProjectRole requireMember(long projectId, long userId) {
        List<String> roles = jdbc.queryForList(
                "SELECT project_role FROM project_members WHERE project_id = ? AND user_id = ?",
                String.class, projectId, userId);
        if (roles.isEmpty()) {
            throw ApiException.notFound();
        }
        return ProjectRole.valueOf(roles.get(0));
    }

    /** Manager-only actions: 404 for non-members, 403 for Members (D10). */
    @Transactional(readOnly = true)
    public void requireManager(long projectId, long userId) {
        if (requireMember(projectId, userId) != ProjectRole.MANAGER) {
            throw ApiException.forbidden();
        }
    }

    /**
     * Reading project data and members: members, and the Admin account (which sees project names and
     * members, but not tasks or comments). Returns the role; null for the Admin.
     */
    @Transactional(readOnly = true)
    public ProjectRole requireReader(long projectId, AuthenticatedUser user) {
        if (isAdmin(user)) {
            requireExists(projectId);
            return null;
        }
        return requireMember(projectId, user.id());
    }

    /** Changing a project or its members: project Managers; the Admin gets 403 (D11), others 403 or 404. */
    @Transactional(readOnly = true)
    public void requireManager(long projectId, AuthenticatedUser user) {
        if (isAdmin(user)) {
            requireExists(projectId);
            throw ApiException.forbidden();
        }
        requireManager(projectId, user.id());
    }

    /** Locks the project row until the end of the transaction (D16, D25); 404 if it no longer exists (D26). */
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedProject lock(long projectId) {
        List<LockedProject> rows = jdbc.query(
                "SELECT id, name, description, version FROM projects WHERE id = ? FOR UPDATE",
                (rs, i) -> new LockedProject(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getInt("version")),
                projectId);
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return rows.get(0);
    }

    public static boolean isAdmin(AuthenticatedUser user) {
        return "ADMIN".equals(user.accountRole());
    }

    private void requireExists(long projectId) {
        Integer found = jdbc.queryForObject("SELECT count(*) FROM projects WHERE id = ?", Integer.class, projectId);
        if (found == null || found == 0) {
            throw ApiException.notFound();
        }
    }
}

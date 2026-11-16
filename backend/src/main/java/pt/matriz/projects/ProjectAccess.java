package pt.matriz.projects;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.web.ApiException;

/**
 * Access rules on a project (docs/implementation-plan.md section 4), checked on every request from
 * the database, never cached in the session (D17, D18). Used by the projects, tasks and comments
 * services for every operation on a project and on its tasks, comments and assignees.
 */
@Service
public class ProjectAccess {

    public enum ProjectRole { MANAGER, MEMBER }

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
}

package db.seeddev;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Demo data for the development profile only (A25, docs/implementation-plan.md M1 to M3).
 *
 * <p>Flyway runs it once and records it in its history table, so later start-ups never repeat it
 * and never delete data. Version 2.1 places it right after the schema and grants, so later
 * migrations (V3, V4...) still run in order. It creates, with fictitious data:
 * <ul>
 *   <li>a demo Manager account and a demo Member account, without a forced password change;</li>
 *   <li>project "Website redesign" with both users (different access from the next one);</li>
 *   <li>project "Annual report" with the Manager only;</li>
 *   <li>a few tasks in each status.</li>
 * </ul>
 * The password of both accounts comes from the MATRIZ_DEMO_PASSWORD environment variable and is
 * stored only as a BCrypt hash.
 */
public class V2_1__Demo_data extends BaseJavaMigration {

    static final String MANAGER_EMAIL = "manager@example.test";
    static final String MEMBER_EMAIL = "member@example.test";

    @Override
    public void migrate(Context context) throws Exception {
        String password = context.getConfiguration().getPlaceholders().get("demoPassword");
        if (password == null || password.isBlank() || password.length() < 8) {
            throw new IllegalStateException(
                    "MATRIZ_DEMO_PASSWORD must be set (at least 8 characters) to create the demo accounts");
        }
        String hash = new BCryptPasswordEncoder().encode(password);
        Connection db = context.getConnection();

        long manager = insertUser(db, "Gestor Demo", MANAGER_EMAIL, hash, "MANAGER");
        long member = insertUser(db, "Membro Demo", MEMBER_EMAIL, hash, "MEMBER");
        audit(db, manager, "ACCOUNT_CREATED", null, null);
        audit(db, member, "ACCOUNT_CREATED", null, null);

        long website = insertProject(db, "Website redesign", "New layout for the public website (demo data).", manager);
        addMember(db, website, "Website redesign", manager, "MANAGER");
        addMember(db, website, "Website redesign", member, "MEMBER");

        long report = insertProject(db, "Annual report", "Visible only to the demo Manager (demo data).", manager);
        addMember(db, report, "Annual report", manager, "MANAGER");

        long t1 = insertTask(db, website, "Define the new colour palette", "TODO", "HIGH", manager);
        insertTask(db, website, "Write the home page texts", "TODO", "MEDIUM", member);
        long t3 = insertTask(db, website, "Build the page templates", "IN_PROGRESS", "HIGH", manager);
        insertTask(db, website, "Collect feedback on the old site", "DONE", "LOW", member);
        assign(db, t1, website, member);
        assign(db, t3, website, manager);
        assign(db, t3, website, member);

        insertTask(db, report, "Gather the yearly figures", "IN_PROGRESS", "MEDIUM", manager);
        insertTask(db, report, "Review the draft", "TODO", "LOW", manager);
    }

    private long insertUser(Connection db, String name, String email, String hash, String role) throws SQLException {
        return insertReturningId(db,
                "INSERT INTO users (name, email, password_hash, must_change_password, account_role) "
                        + "VALUES (?, ?, ?, false, ?) RETURNING id",
                name, email, hash, role);
    }

    private long insertProject(Connection db, String name, String description, long createdBy) throws SQLException {
        return insertReturningId(db,
                "INSERT INTO projects (name, description, created_by) VALUES (?, ?, ?) RETURNING id",
                name, description, createdBy);
    }

    private void addMember(Connection db, long projectId, String projectName, long userId, String role)
            throws SQLException {
        execute(db, "INSERT INTO project_members (project_id, user_id, project_role) VALUES (?, ?, ?)",
                projectId, userId, role);
        audit(db, userId, "MEMBER_ADDED", projectId, projectName);
    }

    private long insertTask(Connection db, long projectId, String title, String status, String priority,
                            long createdBy) throws SQLException {
        return insertReturningId(db,
                "INSERT INTO tasks (project_id, title, status, priority, created_by) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                projectId, title, status, priority, createdBy);
    }

    private void assign(Connection db, long taskId, long projectId, long userId) throws SQLException {
        execute(db, "INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)",
                taskId, projectId, userId);
    }

    /** System action: actor_id is NULL for seed data (docs/database.md, user_audit_log). */
    private void audit(Connection db, long targetUserId, String action, Long projectId, String projectName)
            throws SQLException {
        execute(db, "INSERT INTO user_audit_log (actor_id, target_user_id, action, project_id, project_name) "
                + "VALUES (NULL, ?, ?, ?, ?)", targetUserId, action, projectId, projectName);
    }

    private long insertReturningId(Connection db, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(db, sql, params); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void execute(Connection db, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(db, sql, params)) {
            ps.executeUpdate();
        }
    }

    private PreparedStatement prepare(Connection db, String sql, Object... params) throws SQLException {
        PreparedStatement ps = db.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }
}

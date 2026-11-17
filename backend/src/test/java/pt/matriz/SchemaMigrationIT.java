package pt.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Schema created by Flyway without the dev seed (production-like): tables, constraints and the
 * privileges of the application user (docs/database.md, sections 2 and 6).
 * Each test runs in a transaction that is rolled back, so tests do not affect each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test") // not "dev": no demo data, even if the local .env enables it
@Transactional
class SchemaMigrationIT {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
    }

    @Autowired
    JdbcTemplate jdbc; // connected as the application user

    @Autowired
    Flyway flyway;

    @Test
    void appliesSchemaAndGrantsButNoDemoData() {
        assertThat(flyway.info().applied()).extracting(m -> m.getVersion().getVersion())
                .containsExactly("1", "2");
        assertThat(count("SELECT count(*) FROM users WHERE account_role <> 'ADMIN'")).isZero();
        assertThat(count("SELECT count(*) FROM projects")).isZero();
    }

    @Test
    void createsOneAdminThatMustChangePassword() {
        assertThat(count("SELECT count(*) FROM users WHERE account_role = 'ADMIN' AND must_change_password")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_audit_log WHERE action = 'ACCOUNT_CREATED'")).isEqualTo(1);
    }

    @Test
    void refusesDuplicateEmailIgnoringCase() { // D1
        long id = user("Ana", "ana@example.test");
        assertThat(id).isPositive();
        assertThatThrownBy(() -> user("Ana B", "ANA@Example.TEST"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTaskTitleMadeOnlyOfSpaces() { // D20, E5
        long manager = user("Rui", "rui@example.test");
        long project = project("Alpha", manager);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tasks (project_id, title, created_by) VALUES (?, '     ', ?)", project, manager))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appliesTaskDefaults() { // A5
        long manager = user("Rui", "rui@example.test");
        long project = project("Alpha", manager);
        jdbc.update("INSERT INTO tasks (project_id, title, created_by) VALUES (?, 'Fix login', ?)", project, manager);
        assertThat(jdbc.queryForList("SELECT status, priority FROM tasks WHERE project_id = ?", project))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("status")).isEqualTo("TODO");
                    assertThat(row.get("priority")).isEqualTo("MEDIUM");
                });
    }

    @Test
    void refusesAssigneeWhoIsNotAProjectMember() { // D22
        long manager = user("Rui", "rui@example.test");
        long outsider = user("Eva", "eva@example.test");
        long project = project("Alpha", manager);
        long task = task(project, manager);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", task, project, outsider))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesSameAssigneeTwice() { // D21
        long manager = user("Rui", "rui@example.test");
        long project = project("Alpha", manager);
        long task = task(project, manager);
        jdbc.update("INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", task, project, manager);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", task, project, manager))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAProjectLeavesNoOrphans() { // A11, D29
        long manager = user("Rui", "rui@example.test");
        long project = project("Alpha", manager);
        long task = task(project, manager);
        jdbc.update("INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", task, project, manager);
        jdbc.update("INSERT INTO comments (task_id, author_id, body) VALUES (?, ?, 'Hello')", task, manager);

        jdbc.update("DELETE FROM projects WHERE id = ?", project);

        assertThat(count("SELECT count(*) FROM tasks")).isZero();
        assertThat(count("SELECT count(*) FROM task_assignees")).isZero();
        assertThat(count("SELECT count(*) FROM comments")).isZero();
        assertThat(count("SELECT count(*) FROM project_members")).isZero();
    }

    @Test
    void applicationUserCannotChangeTheActivityLog() { // D39
        assertThatThrownBy(() -> jdbc.update("DELETE FROM activity_log"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void applicationUserCannotChangeTheUserAuditLog() { // D39
        assertThatThrownBy(() -> jdbc.update("UPDATE user_audit_log SET action = 'ACCOUNT_LOCKED'"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }

    private long user(String name, String email) {
        return jdbc.queryForObject("INSERT INTO users (name, email, password_hash, account_role) "
                + "VALUES (?, ?, 'not-a-real-hash', 'MANAGER') RETURNING id", Long.class, name, email);
    }

    private long project(String name, long manager) {
        long id = jdbc.queryForObject("INSERT INTO projects (name, created_by) VALUES (?, ?) RETURNING id",
                Long.class, name, manager);
        jdbc.update("INSERT INTO project_members (project_id, user_id, project_role) VALUES (?, ?, 'MANAGER')",
                id, manager);
        return id;
    }

    private long task(long project, long creator) {
        return jdbc.queryForObject("INSERT INTO tasks (project_id, title, created_by) VALUES (?, 'Task one', ?) "
                + "RETURNING id", Long.class, project, creator);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}

package pt.matriz;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.accounts.AdminAccountInitializer;

/**
 * Demo data of the dev profile (A25, docs/implementation-plan.md M1 to M3): two demo users with
 * different access, two projects, and no duplicates when the start-up steps run again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("dev")
class DevSeedIT {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    AdminAccountInitializer adminInitializer;

    @Test
    void createsTwoDemoUsersWithoutForcedPasswordChange() {
        assertThat(jdbc.queryForList(
                "SELECT email FROM users WHERE account_role <> 'ADMIN' AND NOT must_change_password ORDER BY email",
                String.class))
                .containsExactly("manager@example.test", "member@example.test");
    }

    @Test
    void storesTheDemoPasswordOnlyAsABcryptHash() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'member@example.test'",
                String.class);
        assertThat(hash).startsWith("$2").isNotEqualTo("test-demo-password");
        assertThat(new BCryptPasswordEncoder().matches("test-demo-password", hash)).isTrue();
    }

    @Test
    void givesTheDemoUsersDifferentAccess() {
        assertThat(projectsOf("manager@example.test")).containsExactly("Annual report", "Website redesign");
        assertThat(projectsOf("member@example.test")).containsExactly("Website redesign");
    }

    @Test
    void createsTasksInEveryStatus() {
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM tasks ORDER BY status", String.class))
                .containsExactly("DONE", "IN_PROGRESS", "TODO");
        assertThat(count("SELECT count(*) FROM tasks")).isEqualTo(6);
    }

    @Test
    void runningTheStartupStepsAgainDuplicatesNothing() throws Exception {
        int users = count("SELECT count(*) FROM users");
        int projects = count("SELECT count(*) FROM projects");
        int tasks = count("SELECT count(*) FROM tasks");

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        adminInitializer.run(new DefaultApplicationArguments());

        assertThat(count("SELECT count(*) FROM users")).isEqualTo(users);
        assertThat(count("SELECT count(*) FROM projects")).isEqualTo(projects);
        assertThat(count("SELECT count(*) FROM tasks")).isEqualTo(tasks);
        assertThat(count("SELECT count(*) FROM users WHERE account_role = 'ADMIN'")).isEqualTo(1);
    }

    private java.util.List<String> projectsOf(String email) {
        return jdbc.queryForList("SELECT p.name FROM projects p JOIN project_members m ON m.project_id = p.id "
                + "JOIN users u ON u.id = m.user_id WHERE u.email = ? ORDER BY p.name", String.class, email);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}

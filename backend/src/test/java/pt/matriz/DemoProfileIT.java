package pt.matriz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Demo profile of the public course deployment (docs/deployment.md): the same demo accounts, projects
 * and tasks as the dev profile, without activating dev.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("demo")
class DemoProfileIT {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsTheDemoAccountsProjectsAndTasks() {
        assertThat(jdbc.queryForList(
                "SELECT email FROM users WHERE account_role <> 'ADMIN' AND NOT must_change_password ORDER BY email",
                String.class))
                .containsExactly("manager@example.test", "member@example.test");
        assertThat(jdbc.queryForList("SELECT name FROM projects ORDER BY name", String.class))
                .containsExactly("Annual report", "Website redesign");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Integer.class)).isEqualTo(6);
    }
}

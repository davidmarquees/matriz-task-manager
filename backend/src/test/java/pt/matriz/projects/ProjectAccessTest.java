package pt.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.PostgresTestDatabase;
import pt.matriz.web.ApiException;

/** Access rules with the two demo users, who have different access (docs/implementation-plan.md §4). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("dev")
class ProjectAccessTest {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
    }

    @Autowired
    ProjectAccess access;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void managerIsManagerOfBothDemoProjects() {
        long manager = user("manager@example.test");
        assertThat(access.requireMember(project("Website redesign"), manager)).isEqualTo(ProjectAccess.ProjectRole.MANAGER);
        assertThat(access.requireMember(project("Annual report"), manager)).isEqualTo(ProjectAccess.ProjectRole.MANAGER);
    }

    @Test
    void memberDoesNotSeeAProjectTheyDoNotBelongTo() { // E4, D17
        assertThatThrownBy(() -> access.requireMember(project("Annual report"), user("member@example.test")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void memberCannotDoManagerActions() { // D10
        assertThatThrownBy(() -> access.requireManager(project("Website redesign"), user("member@example.test")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownProjectLooksTheSameAsAForeignOne() { // E4
        assertThatThrownBy(() -> access.requireMember(999_999, user("manager@example.test")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private long user(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long project(String name) {
        return jdbc.queryForObject("SELECT id FROM projects WHERE name = ?", Long.class, name);
    }
}

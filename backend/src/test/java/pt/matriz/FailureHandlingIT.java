package pt.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.audit.ActivityLog;
import pt.matriz.tasks.TaskCounts;

/**
 * Unexpected failures (docs/database.md D28, D29, D40): a generic 500 without internal details, and
 * nothing saved halfway when a step of a change fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class FailureHandlingIT {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
        registry.add("matriz.login.rate-limit.max-attempts", () -> "10000");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    /** A plain mock (no transaction proxy), so it can be told to fail; it writes nothing in this class. */
    @MockitoBean
    ActivityLog activity;

    @MockitoSpyBean
    TaskCounts taskCounts;

    @AfterEach
    void restore() {
        reset(activity, taskCounts);
    }

    @Test
    void databaseFailureGivesAGenericAnswerWithoutInternalDetails() throws Exception { // D28, NFR5
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "viewer"), PASSWORD);
        mvc.perform(post("/projects").session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Visible project\"}")).andExpect(status().isCreated());
        doThrow(new DataAccessResourceFailureException("Connection to db-host:5432 refused; SELECT secret FROM users"))
                .when(taskCounts).byProject(any());

        String body = mvc.perform(get("/projects").session(manager))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"status\":500").contains("Internal Server Error")
                .doesNotContain("db-host").doesNotContain("SELECT").doesNotContain("Exception").doesNotContain("at pt.");
    }

    @Test
    void whenTheActivityEntryCannotBeWrittenNothingIsSaved() throws Exception { // D29, D40
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "writer"), PASSWORD);
        doThrow(new DataAccessResourceFailureException("disk full"))
                .when(activity).record(anyLong(), anyString(), anyLong(), any(), anyLong(), any(), any());

        mvc.perform(post("/projects").session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Never saved\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects WHERE name = 'Never saved'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members m JOIN projects p ON p.id = m.project_id "
                + "WHERE p.name = 'Never saved'", Integer.class)).isZero();
    }
}

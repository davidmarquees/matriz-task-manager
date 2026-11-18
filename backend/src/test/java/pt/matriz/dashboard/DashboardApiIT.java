package pt.matriz.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/**
 * Dashboard data for the UI polish (2026-11-18): open tasks per priority in getDashboard, and
 * listDashboardDueTasks (range, order, scope to the user's projects, validation, Admin 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class DashboardApiIT {

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

    @Autowired
    ObjectMapper json;

    MockHttpSession manager;
    MockHttpSession outsider;
    long projectA;
    long projectB;
    long otherProject;

    @BeforeEach
    void twoProjectsOfTheManagerAndOneOfSomeoneElse() throws Exception {
        manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "dash"), PASSWORD);
        outsider = login(mvc, createUser(jdbc, encoder, "MANAGER", "other"), PASSWORD);
        projectA = project(manager, "Alpha");
        projectB = project(manager, "Beta");
        otherProject = project(outsider, "Not mine");
    }

    @Test
    void priorityCountsCoverOnlyOpenTasksOfTheUsersProjects() throws Exception {
        task(projectA, "{\"title\":\"High open\",\"priority\":\"HIGH\"}");
        task(projectB, "{\"title\":\"High in progress\",\"priority\":\"HIGH\",\"status\":\"IN_PROGRESS\"}");
        task(projectB, "{\"title\":\"Low open\",\"priority\":\"LOW\"}");
        task(projectA, "{\"title\":\"Medium done\",\"priority\":\"MEDIUM\",\"status\":\"DONE\"}");
        send(post("/projects/" + otherProject + "/tasks"), outsider, "{\"title\":\"Someone else's\",\"priority\":\"MEDIUM\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/dashboard").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.priorityCounts.HIGH").value(2))
                .andExpect(jsonPath("$.totals.priorityCounts.MEDIUM").value(0)) // the only MEDIUM task is DONE
                .andExpect(jsonPath("$.totals.priorityCounts.LOW").value(1))
                .andExpect(jsonPath("$.totals.taskCounts.DONE").value(1));
    }

    @Test
    void dueTasksAreTheUsersTasksInTheRangeSortedByDateThenPriority() throws Exception {
        task(projectA, "{\"title\":\"Before\",\"dueDate\":\"2026-11-15\"}");
        task(projectA, "{\"title\":\"Wed medium\",\"priority\":\"MEDIUM\",\"dueDate\":\"2026-11-18\"}");
        task(projectB, "{\"title\":\"Wed high\",\"priority\":\"HIGH\",\"dueDate\":\"2026-11-18\",\"status\":\"DONE\"}");
        task(projectB, "{\"title\":\"Mon\",\"priority\":\"LOW\",\"dueDate\":\"2026-11-16\"}");
        task(projectA, "{\"title\":\"Sun\",\"dueDate\":\"2026-11-22\"}");
        task(projectA, "{\"title\":\"After\",\"dueDate\":\"2026-11-23\"}");
        task(projectA, "{\"title\":\"No due date\"}");
        send(post("/projects/" + otherProject + "/tasks"), outsider, "{\"title\":\"Not visible\",\"dueDate\":\"2026-11-18\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/dashboard/due-tasks").param("from", "2026-11-16").param("to", "2026-11-22").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].title").value("Mon"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-11-16"))
                .andExpect(jsonPath("$.items[0].projectName").value("Beta"))
                .andExpect(jsonPath("$.items[1].title").value("Wed high"))
                .andExpect(jsonPath("$.items[1].status").value("DONE"))
                .andExpect(jsonPath("$.items[1].priority").value("HIGH"))
                .andExpect(jsonPath("$.items[2].title").value("Wed medium"))
                .andExpect(jsonPath("$.items[2].projectId").value(projectA))
                .andExpect(jsonPath("$.items[3].title").value("Sun"));
    }

    @Test
    void aUserWithoutProjectsGetsEmptyResults() throws Exception {
        MockHttpSession newcomer = login(mvc, createUser(jdbc, encoder, "MEMBER", "new"), PASSWORD);

        mvc.perform(get("/dashboard").session(newcomer))
                .andExpect(jsonPath("$.totals.priorityCounts.HIGH").value(0))
                .andExpect(jsonPath("$.totals.priorityCounts.MEDIUM").value(0))
                .andExpect(jsonPath("$.totals.priorityCounts.LOW").value(0));
        mvc.perform(get("/dashboard/due-tasks").param("from", "2026-11-16").param("to", "2026-11-22").session(newcomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void theRangeIsValidated() throws Exception {
        expectError(due(null, "2026-11-22"), "from", "REQUIRED");
        expectError(due("2026-11-16", null), "to", "REQUIRED");
        expectError(due("16/11/2026", "2026-11-22"), "from", "INVALID_DATE");
        expectError(due("2026-11-16", "2026-02-30"), "to", "INVALID_DATE");
        expectError(due("2026-11-22", "2026-11-16"), "to", "INVALID_VALUE");
        expectError(due("2026-11-01", "2026-12-13"), "to", "INVALID_VALUE"); // 43 days
        due("2026-11-01", "2026-12-12").andExpect(status().isOk()); // 42 days, the limit
        due("2026-11-18", "2026-11-18").andExpect(status().isOk()); // one day
    }

    @Test
    void theAdminHasNoDashboard() throws Exception {
        MockHttpSession admin = login(mvc, createUser(jdbc, encoder, "ADMIN", "root"), PASSWORD);

        mvc.perform(get("/dashboard").session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard/due-tasks").param("from", "2026-11-16").param("to", "2026-11-22").session(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mvc.perform(get("/dashboard/due-tasks").param("from", "2026-11-16").param("to", "2026-11-22"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions due(String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = get("/dashboard/due-tasks").session(manager);
        if (from != null) {
            request.param("from", from);
        }
        if (to != null) {
            request.param("to", to);
        }
        return mvc.perform(request);
    }

    private void expectError(ResultActions result, String field, String code) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    private long project(MockHttpSession session, String name) throws Exception {
        return json.readTree(send(post("/projects"), session, "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private void task(long projectId, String body) throws Exception {
        send(post("/projects/" + projectId + "/tasks"), manager, body).andExpect(status().isCreated());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

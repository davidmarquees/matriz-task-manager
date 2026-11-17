package pt.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;
import static pt.matriz.ApiTestSupport.userId;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/**
 * Tasks operations over HTTP (US3 to US6, FR5 to FR10, D20 to D27, D43, E2, E3), plus the "last change"
 * extra (option A) and getDashboard. Every test builds its own project with a Manager and a Member.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class TasksApiIT {

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
    MockHttpSession member;
    long memberId;
    long managerId;
    long projectId;

    @BeforeEach
    void projectWithManagerAndMember() throws Exception {
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "lead");
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "dev");
        manager = login(mvc, managerEmail, PASSWORD);
        member = login(mvc, memberEmail, PASSWORD);
        managerId = userId(jdbc, managerEmail);
        memberId = userId(jdbc, memberEmail);
        projectId = json.readTree(send(post("/projects"), manager, "{\"name\":\"Tasks project\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        send(post("/projects/" + projectId + "/members"), manager, "{\"email\":\"" + memberEmail + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void memberCreatesATaskWithDefaultsThatIsSavedAndReadBack() throws Exception { // US3, A5, persistence
        JsonNode task = created(createTask(member, "{\"title\":\"  Fix login  \"}"));
        long id = task.get("id").asLong();

        assertThat(task.get("title").asText()).isEqualTo("Fix login");
        assertThat(task.get("status").asText()).isEqualTo("TODO");
        assertThat(task.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(task.get("createdBy").get("id").asLong()).isEqualTo(memberId);
        assertThat(task.get("lastChange").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_by FROM tasks WHERE id = ?", Long.class, id)).isEqualTo(memberId);

        mvc.perform(get("/tasks/" + id).session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fix login"))
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @Test
    void refusesInvalidTaskData() throws Exception { // D20, E5, E7
        expectError(createTask(member, "{\"title\":\"     \"}"), "title", "BLANK");
        expectError(createTask(member, "{\"title\":\"ab\"}"), "title", "TOO_SHORT");
        expectError(createTask(member, "{\"title\":\"" + "x".repeat(151) + "\"}"), "title", "TOO_LONG");
        expectError(createTask(member, "{}"), "title", "REQUIRED");
        expectError(createTask(member, "{\"title\":\"Valid\",\"description\":\"" + "d".repeat(2001) + "\"}"),
                "description", "TOO_LONG");
        expectError(createTask(member, "{\"title\":\"Valid\",\"status\":\"FINISHED\"}"), "status", "INVALID_VALUE");
        expectError(createTask(member, "{\"title\":\"Valid\",\"priority\":\"URGENT\"}"), "priority", "INVALID_VALUE");
        expectError(createTask(member, "{\"title\":\"Valid\",\"dueDate\":\"2026-13-40\"}"), "dueDate", "INVALID_DATE");
        expectError(createTask(member, "{\"title\":\"Valid\",\"assigneeIds\":[" + memberId + "," + memberId + "]}"),
                "assigneeIds", "DUPLICATE");
        createTask(member, "{\"title\":\"Valid\",\"createdBy\":1}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE project_id = ?", Integer.class, projectId)).isZero();
    }

    @Test
    void assigneesMustBeActiveProjectMembers() throws Exception { // FR8, D22
        long outsider = userId(jdbc, createUser(jdbc, encoder, "MEMBER", "outsider"));
        expectError(createTask(member, "{\"title\":\"Shared\",\"assigneeIds\":[" + outsider + "]}"),
                "assigneeIds", "NOT_A_PROJECT_MEMBER");

        JsonNode task = created(createTask(member, "{\"title\":\"Shared\",\"assigneeIds\":[" + memberId + "," + managerId + "]}"));
        assertThat(task.get("assignees")).hasSize(2);
    }

    @Test
    void anyMemberEditsAndStaleOrEmptyChangesAreHandled() throws Exception { // A13, D25, D43
        long id = created(createTask(manager, "{\"title\":\"Write report\"}")).get("id").asLong();

        send(patch("/tasks/" + id), member, "{\"version\":0,\"status\":\"TODO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)); // no real change
        send(patch("/tasks/" + id), member, "{\"version\":0,\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\",\"dueDate\":\"2020-01-31\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2020-01-31")) // past dates accepted (A19)
                .andExpect(jsonPath("$.version").value(1));
        send(patch("/tasks/" + id), manager, "{\"version\":0,\"title\":\"Stale\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(patch("/tasks/" + id), manager, "{\"version\":1,\"projectId\":999}").andExpect(status().isBadRequest()); // D27
        send(patch("/tasks/" + id), manager, "{\"version\":1,\"title\":null}").andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id = ?", String.class, id)).isEqualTo("Write report");
    }

    @Test
    void onlyManagersOrTheCreatorDeleteTasks() throws Exception { // A10, D23
        long byManager = created(createTask(manager, "{\"title\":\"Manager task\"}")).get("id").asLong();
        long byMember = created(createTask(member, "{\"title\":\"Member task\"}")).get("id").asLong();

        mvc.perform(delete("/tasks/" + byManager).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/tasks/" + byMember).session(member).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/tasks/" + byManager).session(manager).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/tasks/" + byManager).session(manager)).andExpect(status().isNotFound()); // D26
        assertThat(jdbc.queryForObject("SELECT changes::text FROM activity_log WHERE entity_type = 'TASK' AND entity_id = ? "
                + "AND action = 'DELETED'", String.class, byManager)).contains("Manager task");
    }

    @Test
    void outsidersAndTheAdminCannotReachTasks() throws Exception { // E4, D11, D17
        long id = created(createTask(member, "{\"title\":\"Private task\"}")).get("id").asLong();
        MockHttpSession outsider = login(mvc, createUser(jdbc, encoder, "MANAGER", "stranger"), PASSWORD);

        mvc.perform(get("/tasks/" + id).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + projectId + "/tasks").session(outsider)).andExpect(status().isNotFound());
        send(patch("/tasks/" + id), outsider, "{\"version\":0,\"title\":\"Taken\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/tasks/" + id).session(outsider).with(csrf())).andExpect(status().isNotFound());
        createTask(outsider, "{\"title\":\"Injected\"}").andExpect(status().isNotFound());

        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        MockHttpSession admin = login(mvc, "admin@example.test", "test-admin-password");
        mvc.perform(get("/tasks/" + id).session(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/projects/" + projectId + "/tasks").session(admin)).andExpect(status().isForbidden());
    }

    @Test
    void searchFiltersAndPaginationOverTheWholeProject() throws Exception { // US6, E3, P7
        created(createTask(manager, "{\"title\":\"Fix login\",\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\"}"));
        created(createTask(manager, "{\"title\":\"Write report\",\"description\":\"About the LOGIN page\"}"));
        created(createTask(manager, "{\"title\":\"Plan sprint\",\"status\":\"IN_PROGRESS\",\"priority\":\"LOW\","
                + "\"assigneeIds\":[" + memberId + "]}"));
        String tasks = "/projects/" + projectId + "/tasks";

        mvc.perform(get(tasks).param("q", "login").session(member))
                .andExpect(jsonPath("$.totalItems").value(2)); // title or description
        mvc.perform(get(tasks).param("status", "IN_PROGRESS").param("priority", "HIGH").session(member))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Fix login"));
        mvc.perform(get(tasks).param("assigneeId", String.valueOf(memberId)).session(member))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Plan sprint"));
        mvc.perform(get(tasks).param("q", "nothing like this").session(member))
                .andExpect(jsonPath("$.totalItems").value(0)).andExpect(jsonPath("$.items", hasSize(0))); // E3
        mvc.perform(get(tasks).param("size", "2").param("page", "1").session(member))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Fix login")) // newest first
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(tasks).param("status", "LATE").session(member)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void countsFollowEveryChange() throws Exception { // US5, FR9, E2
        mvc.perform(get("/projects/" + projectId).session(member))
                .andExpect(jsonPath("$.taskCounts.TODO").value(0))
                .andExpect(jsonPath("$.taskCounts.DONE").value(0)); // E2
        long id = created(createTask(member, "{\"title\":\"Count me\"}")).get("id").asLong();
        created(createTask(member, "{\"title\":\"Count me too\",\"status\":\"IN_PROGRESS\"}"));
        send(patch("/tasks/" + id), member, "{\"version\":0,\"status\":\"DONE\"}").andExpect(status().isOk());

        mvc.perform(get("/projects/" + projectId).session(member))
                .andExpect(jsonPath("$.taskCounts.TODO").value(0))
                .andExpect(jsonPath("$.taskCounts.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.taskCounts.DONE").value(1));
        mvc.perform(get("/dashboard").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.projects").value(1))
                .andExpect(jsonPath("$.totals.taskCounts.DONE").value(1))
                .andExpect(jsonPath("$.projects[0].id").value(projectId));
    }

    @Test
    void lastChangeShowsWhoAndWhatOnlyToManagers() throws Exception { // extra feature, option A
        long id = created(createTask(manager, "{\"title\":\"Tracked\",\"description\":\"Old text\"}")).get("id").asLong();
        send(patch("/tasks/" + id), member, "{\"version\":0,\"status\":\"IN_PROGRESS\",\"description\":\"New text\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/tasks/" + id).session(manager))
                .andExpect(jsonPath("$.lastChange.changedAt").exists())
                .andExpect(jsonPath("$.lastChange.changedBy.id").value(memberId))
                .andExpect(jsonPath("$.lastChange.changes[?(@.field == 'status')].old").value("TODO"))
                .andExpect(jsonPath("$.lastChange.changes[?(@.field == 'status')].new").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.lastChange.changes[?(@.field == 'description')].old").value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(get("/tasks/" + id).session(member))
                .andExpect(jsonPath("$.lastChange.changedAt").exists())
                .andExpect(jsonPath("$.lastChange.changedBy").doesNotExist())
                .andExpect(jsonPath("$.lastChange.changes").doesNotExist());
        mvc.perform(get("/projects/" + projectId + "/tasks").session(manager))
                .andExpect(jsonPath("$.items[0].lastChange.changedBy.id").value(memberId));
    }

    private ResultActions createTask(MockHttpSession session, String body) throws Exception {
        return send(post("/projects/" + projectId + "/tasks"), session, body);
    }

    private JsonNode created(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void expectError(ResultActions result, String field, String code) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

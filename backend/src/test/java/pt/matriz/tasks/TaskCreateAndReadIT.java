package pt.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;
import static pt.matriz.ApiTestSupport.userId;

import java.util.Map;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/**
 * Worksheet 04, Ex. 3: create a task through the API, read it back and check what was saved, plus the negative
 * cases of docs/test-plan.md (T6, T9, T10, invalid title, missing resources). Expected results come from
 * openapi.yaml (createTask, getTask, Task schema) and docs/database.md section 6, not from the code.
 *
 * <p>Isolation: this class has its own empty PostgreSQL container (dev seed only); every test creates its own
 * users and project with unique emails, so tests never depend on each other or on earlier runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class TaskCreateAndReadIT {

    private static final String DEMO_PASSWORD = "test-demo-password"; // fictitious, PostgresTestDatabase

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
    long managerId;
    long memberId;
    long projectId;

    @BeforeEach
    void projectWithManagerAndMember() throws Exception {
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "ex3-lead");
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "ex3-dev");
        manager = login(mvc, managerEmail, PASSWORD);
        member = login(mvc, memberEmail, PASSWORD);
        managerId = userId(jdbc, managerEmail);
        memberId = userId(jdbc, memberEmail);
        projectId = body(send(post("/projects"), manager, "{\"name\":\"Ex. 3 project\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        send(post("/projects/" + projectId + "/members"), manager, "{\"email\":\"" + memberEmail + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void memberCreatesATaskThatIsReturnedSavedAndReadBack() throws Exception { // T6: US3, FR5 to FR8
        String request = "{\"title\":\"Write the sprint report\",\"description\":\"Summary of the week.\","
                + "\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\",\"dueDate\":\"2026-12-01\","
                + "\"assigneeIds\":[" + memberId + "," + managerId + "]}";

        // POST through the real context path, so the Location header can be compared with the contract
        MvcResult result = mvc.perform(post("/api/projects/" + projectId + "/tasks").contextPath("/api")
                        .session(member).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        JsonNode created = json.readTree(result.getResponse().getContentAsString());
        long id = created.get("id").asLong();

        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/tasks/" + id);
        assertTaskFields(created, id);
        assertThat(created.get("version").asInt()).isZero();

        // read back through the API: same data
        JsonNode read = body(mvc.perform(get("/tasks/" + id).session(member)).andExpect(status().isOk()));
        assertTaskFields(read, id);
        assertThat(read.get("createdAt").asText()).isEqualTo(created.get("createdAt").asText());
        assertThat(read.get("version").asInt()).isEqualTo(created.get("version").asInt());

        // the Manager of the project reads the same task (any project member, FR4)
        mvc.perform(get("/tasks/" + id).session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write the sprint report"));

        // persisted: row and assignees in the database
        Map<String, Object> row = jdbc.queryForMap("SELECT project_id, title, description, status, priority, "
                + "due_date::text AS due_date, created_by FROM tasks WHERE id = ?", id);
        assertThat(row).containsEntry("project_id", projectId).containsEntry("title", "Write the sprint report")
                .containsEntry("description", "Summary of the week.").containsEntry("status", "IN_PROGRESS")
                .containsEntry("priority", "HIGH").containsEntry("due_date", "2026-12-01")
                .containsEntry("created_by", memberId);
        assertThat(jdbc.queryForList("SELECT user_id FROM task_assignees WHERE task_id = ? ORDER BY user_id",
                Long.class, id)).containsExactlyInAnyOrder(memberId, managerId);

        // listed in the project's tasks
        JsonNode page = body(mvc.perform(get("/projects/" + projectId + "/tasks").session(member))
                .andExpect(status().isOk()));
        assertThat(page.get("totalItems").asInt()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("id").asLong()).isEqualTo(id);
    }

    @Test
    void aNewTaskWithoutAssigneesHasNoLastChange() throws Exception { // FR14, US15: "Criada" until changed
        JsonNode created = body(send(post("/projects/" + projectId + "/tasks"), member, "{\"title\":\"Plain task\"}")
                .andExpect(status().isCreated()));
        assertThat(created.get("lastChange").isNull()).isTrue();
    }

    @Test
    void aNewTaskWithAssigneesHasNoLastChange() throws Exception { // FR14, US15, Task.lastChange in openapi.yaml
        JsonNode created = body(send(post("/projects/" + projectId + "/tasks"), member,
                "{\"title\":\"Assigned task\",\"assigneeIds\":[" + memberId + "]}").andExpect(status().isCreated()));
        assertThat(created.get("lastChange").isNull()).isTrue();
    }

    @Test
    void aTitleMadeOnlyOfSpacesIsRefusedAndNothingIsSaved() throws Exception { // T1: E5, D20
        send(post("/projects/" + projectId + "/tasks"), member, "{\"title\":\"     \"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("BLANK"));

        assertThat(taskCount()).isZero();
        assertThat(body(mvc.perform(get("/projects/" + projectId + "/tasks").session(member))
                .andExpect(status().isOk())).get("totalItems").asInt()).isZero();
    }

    @Test
    void aMissingTaskOrProjectLooksTheSameAsAForeignOne() throws Exception { // E4, D17, D26
        long foreignProject = body(send(post("/projects"), login(mvc, createUser(jdbc, encoder, "MANAGER", "ex3-other"),
                PASSWORD), "{\"name\":\"Someone else's project\"}").andExpect(status().isCreated())).get("id").asLong();
        long foreignTask = jdbc.queryForObject("INSERT INTO tasks (project_id, title, created_by) "
                + "SELECT ?, 'Foreign task', created_by FROM projects WHERE id = ? RETURNING id", Long.class,
                foreignProject, foreignProject);

        JsonNode missing = body(mvc.perform(get("/tasks/999999999").session(member)).andExpect(status().isNotFound()));
        JsonNode foreign = body(mvc.perform(get("/tasks/" + foreignTask).session(member)).andExpect(status().isNotFound()));
        // same answer except "instance", which only echoes the requested path
        ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove("instance");
        ((com.fasterxml.jackson.databind.node.ObjectNode) foreign).remove("instance");
        assertThat(missing).isEqualTo(foreign);
        assertThat(foreign.toString()).doesNotContain("Foreign task");

        send(post("/projects/999999999/tasks"), member, "{\"title\":\"Nowhere\"}").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE title = 'Nowhere'", Integer.class)).isZero();
    }

    @Test
    void withoutASessionTaskEndpointsAnswer401() throws Exception { // T10: US1, D8
        long id = body(send(post("/projects/" + projectId + "/tasks"), member, "{\"title\":\"Private\"}")
                .andExpect(status().isCreated())).get("id").asLong();

        mvc.perform(get("/tasks/" + id)).andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private"))));
        mvc.perform(get("/projects/" + projectId + "/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(post("/projects/" + projectId + "/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Anonymous\"}")).andExpect(status().isUnauthorized());
        assertThat(taskCount()).isEqualTo(1);
    }

    @Test
    void theDemoMemberCannotReadOrChangeATaskOfAProjectTheyAreNotIn() throws Exception { // T9: E4, D17, US8
        MockHttpSession demoManager = login(mvc, "manager@example.test", DEMO_PASSWORD);
        MockHttpSession demoMember = login(mvc, "member@example.test", DEMO_PASSWORD);
        long annualReport = jdbc.queryForObject("SELECT id FROM projects WHERE name = 'Annual report'", Long.class);
        long id = body(send(post("/projects/" + annualReport + "/tasks"), demoManager,
                "{\"title\":\"Confidential figures\"}").andExpect(status().isCreated())).get("id").asLong();

        String read = mvc.perform(get("/tasks/" + id).session(demoMember))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(read).doesNotContain("Confidential figures");
        send(patch("/tasks/" + id), demoMember, "{\"version\":0,\"title\":\"Changed by the Member\"}")
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id = ?", String.class, id))
                .isEqualTo("Confidential figures");
        mvc.perform(get("/tasks/" + id).session(demoManager)).andExpect(status().isOk()); // the owner still can
    }

    private void assertTaskFields(JsonNode task, long id) {
        assertThat(task.get("id").asLong()).isEqualTo(id);
        assertThat(task.get("projectId").asLong()).isEqualTo(projectId);
        assertThat(task.get("title").asText()).isEqualTo("Write the sprint report");
        assertThat(task.get("description").asText()).isEqualTo("Summary of the week.");
        assertThat(task.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(task.get("priority").asText()).isEqualTo("HIGH");
        assertThat(task.get("dueDate").asText()).isEqualTo("2026-12-01");
        assertThat(task.get("createdBy").get("id").asLong()).isEqualTo(memberId); // from the session, not the body
        assertThat(task.get("createdBy").get("active").asBoolean()).isTrue();
        assertThat(task.get("assignees").findValuesAsText("id"))
                .containsExactlyInAnyOrder(String.valueOf(memberId), String.valueOf(managerId));
        assertThat(task.hasNonNull("createdAt")).isTrue();
        assertThat(task.hasNonNull("updatedAt")).isTrue();
    }

    private int taskCount() {
        return jdbc.queryForObject("SELECT count(*) FROM tasks WHERE project_id = ?", Integer.class, projectId);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

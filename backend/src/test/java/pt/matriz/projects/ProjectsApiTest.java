package pt.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;
import static pt.matriz.ApiTestSupport.userId;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/**
 * Projects and Members operations of openapi.yaml over HTTP (US2, FR2 to FR4, D9 to D17, D25, D43, E1, E6).
 * Each test creates its own users and projects, so tests do not depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class ProjectsApiTest {

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

    // --- create, then read back from the database (Worksheet 03, Ex. 8: creation + persistence) ---

    @Test
    void managerCreatesAProjectThatIsSavedAndListed() throws Exception {
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "creator");
        MockHttpSession manager = login(mvc, managerEmail, PASSWORD);

        ResultActions result = createProject(manager, "  Website launch  ", "First release")
                .andExpect(status().isCreated());
        JsonNode created = body(result);
        long id = created.get("id").asLong();
        result.andExpect(header().string("Location", endsWith("/projects/" + id)));

        assertThat(created.get("name").asText()).isEqualTo("Website launch"); // trimmed
        assertThat(created.get("myRole").asText()).isEqualTo("MANAGER");
        assertThat(created.get("memberCount").asInt()).isEqualTo(1);
        assertThat(created.get("taskCounts").get("TODO").asInt()).isZero();
        assertThat(created.get("createdBy").get("id").asLong()).isEqualTo(userId(jdbc, managerEmail));

        // persisted with the creator taken from the session, and the creator is its Manager (A12)
        assertThat(jdbc.queryForObject("SELECT created_by FROM projects WHERE id = ?", Long.class, id))
                .isEqualTo(userId(jdbc, managerEmail));
        assertThat(jdbc.queryForObject("SELECT project_role FROM project_members WHERE project_id = ?", String.class, id))
                .isEqualTo("MANAGER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ? AND entity_type = 'PROJECT' "
                + "AND action = 'CREATED'", Integer.class, id)).isEqualTo(1);

        mvc.perform(get("/projects/" + id).session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Website launch"))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(get("/projects").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void onlyManagerAccountsCreateProjects() throws Exception { // D9, D11
        MockHttpSession member = login(mvc, createUser(jdbc, encoder, "MEMBER", "member"), PASSWORD);
        createProject(member, "Not allowed", null).andExpect(status().isForbidden());

        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        MockHttpSession admin = login(mvc, "admin@example.test", "test-admin-password");
        createProject(admin, "Not allowed either", null).andExpect(status().isForbidden());
    }

    @Test
    void refusesInvalidNamesAndDescriptions() throws Exception { // D20
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "validator"), PASSWORD);

        createProject(manager, "   ", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("BLANK"));
        createProject(manager, "x".repeat(101), null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        createProject(manager, "Fine", "d".repeat(2001)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        send(post("/projects"), manager, "{}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        send(post("/projects"), manager, "{\"name\": 123}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
        send(post("/projects"), manager, "{\"name\":\"Mine\",\"createdBy\":1}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projects WHERE name IN ('Fine', 'Mine')", Integer.class))
                .isZero();
    }

    // --- list, search, pagination and visibility ---

    @Test
    void listsOnlyTheUsersProjectsWithSearchAndPagination() throws Exception { // FR4, FR10, P7
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "lister"), PASSWORD);
        createProject(manager, "Alpha website", null).andExpect(status().isCreated());
        createProject(manager, "Beta report", null).andExpect(status().isCreated());
        createProject(manager, "Gamma WEB shop", null).andExpect(status().isCreated());
        MockHttpSession outsider = login(mvc, createUser(jdbc, encoder, "MEMBER", "outsider"), PASSWORD);

        mvc.perform(get("/projects").param("q", "web").session(manager))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Alpha website"))
                .andExpect(jsonPath("$.items[1].name").value("Gamma WEB shop"));
        mvc.perform(get("/projects").param("size", "1").param("page", "1").session(manager))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Beta report"))
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/projects").param("q", "%").session(manager))
                .andExpect(jsonPath("$.totalItems").value(0)); // % is searched as text, not as a wildcard
        mvc.perform(get("/projects").session(outsider))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/projects").param("size", "101").session(manager)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nonMembersCannotSeeAProjectEvenByChangingTheId() throws Exception { // E4, D17
        MockHttpSession owner = login(mvc, createUser(jdbc, encoder, "MANAGER", "owner"), PASSWORD);
        long id = createdId(createProject(owner, "Private plans", null));
        MockHttpSession other = login(mvc, createUser(jdbc, encoder, "MANAGER", "other"), PASSWORD);

        mvc.perform(get("/projects/" + id).session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + id + "/members").session(other)).andExpect(status().isNotFound());
        send(patch("/projects/" + id), other, "{\"version\":0,\"name\":\"Taken\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/projects/" + id).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/projects/999999").session(owner)).andExpect(status().isNotFound());
        mvc.perform(get("/projects/abc").session(owner)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT name FROM projects WHERE id = ?", String.class, id)).isEqualTo("Private plans");
    }

    // --- update ---

    @Test
    void managersUpdateWithVersionMembersCannot() throws Exception { // D10, D25, D43
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "editor");
        MockHttpSession manager = login(mvc, managerEmail, PASSWORD);
        long id = createdId(createProject(manager, "Old name", "Old description"));
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "reader");
        addMember(manager, id, memberEmail, null).andExpect(status().isCreated());
        MockHttpSession member = login(mvc, memberEmail, PASSWORD);

        send(patch("/projects/" + id), member, "{\"version\":0,\"name\":\"Hack\"}").andExpect(status().isForbidden());

        send(patch("/projects/" + id), manager, "{\"version\":0,\"name\":\"Old name\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)); // no real change
        send(patch("/projects/" + id), manager, "{\"version\":0,\"name\":\"New name\",\"description\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.version").value(1));
        send(patch("/projects/" + id), manager, "{\"version\":0,\"name\":\"Stale\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(patch("/projects/" + id), manager, "{\"version\":1}").andExpect(status().isUnprocessableEntity());
        send(patch("/projects/" + id), manager, "{\"version\":1,\"owner\":2}").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT changes::text FROM activity_log WHERE project_id = ? AND action = 'UPDATED'",
                String.class, id)).contains("Old name").contains("New name");
    }

    // --- delete ---

    @Test
    void deletingAProjectRemovesEverythingInsideIt() throws Exception { // US2, A11, D29
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "deleter");
        MockHttpSession manager = login(mvc, managerEmail, PASSWORD);
        long id = createdId(createProject(manager, "Short-lived", null));
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "doomed");
        addMember(manager, id, memberEmail, null).andExpect(status().isCreated());
        long task = jdbc.queryForObject("INSERT INTO tasks (project_id, title, created_by) VALUES (?, 'Task', ?) RETURNING id",
                Long.class, id, userId(jdbc, managerEmail));
        jdbc.update("INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", task, id,
                userId(jdbc, memberEmail));
        jdbc.update("INSERT INTO comments (task_id, author_id, body) VALUES (?, ?, 'Hello')", task, userId(jdbc, memberEmail));
        MockHttpSession member = login(mvc, memberEmail, PASSWORD);

        mvc.perform(delete("/projects/" + id).session(member).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/projects/" + id).session(manager).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/projects/" + id).session(manager)).andExpect(status().isNotFound());
        for (String table : new String[] {"tasks", "project_members", "task_assignees"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE project_id = ?", Integer.class, id))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE task_id = ?", Integer.class, task)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ? AND action = 'DELETED'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_log WHERE project_id = ? AND action = 'MEMBER_REMOVED'",
                Integer.class, id)).isEqualTo(2);
    }

    // --- members ---

    @Test
    void managersAddMembersByEmailWithEligibilityRules() throws Exception { // FR3, D13, D14, D15
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "team"), PASSWORD);
        long id = createdId(createProject(manager, "Team", null));
        String colleague = createUser(jdbc, encoder, "MEMBER", "colleague");
        String leaver = createUser(jdbc, encoder, "MEMBER", "leaver");
        jdbc.update("UPDATE users SET status = 'DEACTIVATED', deactivated_at = now() WHERE email = ?", leaver);

        addMember(manager, id, colleague.toUpperCase(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectRole").value("MEMBER"))
                .andExpect(jsonPath("$.user.id").value(userId(jdbc, colleague)));
        addMember(manager, id, colleague, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        for (String notEligible : new String[] {leaver, "admin@example.test", "nobody@example.test"}) {
            addMember(manager, id, notEligible, null).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("USER_NOT_ELIGIBLE"));
        }
        MockHttpSession member = login(mvc, colleague, PASSWORD);
        addMember(member, id, createUser(jdbc, encoder, "MEMBER", "friend"), null).andExpect(status().isForbidden());

        mvc.perform(get("/projects/" + id + "/members").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/projects/" + id).session(member))
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
    }

    @Test
    void theLastManagerCannotLeaveOrBeDemoted() throws Exception { // E6, D16
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "lonely");
        MockHttpSession manager = login(mvc, managerEmail, PASSWORD);
        long id = createdId(createProject(manager, "Solo", null));
        long managerId = userId(jdbc, managerEmail);

        send(patch("/projects/" + id + "/members/" + managerId), manager, "{\"projectRole\":\"MEMBER\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_MANAGER"));
        mvc.perform(delete("/projects/" + id + "/members/" + managerId).session(manager).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_MANAGER"));

        String deputy = createUser(jdbc, encoder, "MEMBER", "deputy");
        addMember(manager, id, deputy, "MANAGER").andExpect(status().isCreated());
        send(patch("/projects/" + id + "/members/" + managerId), manager, "{\"projectRole\":\"MEMBER\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectRole").value("MEMBER"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_log WHERE project_id = ? "
                + "AND action = 'PROJECT_ROLE_CHANGED'", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void removingAMemberRemovesTheirAssignmentsButKeepsTheTasks() throws Exception { // E1
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "boss");
        MockHttpSession manager = login(mvc, managerEmail, PASSWORD);
        long id = createdId(createProject(manager, "Assignments", null));
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "worker");
        addMember(manager, id, memberEmail, null).andExpect(status().isCreated());
        long memberId = userId(jdbc, memberEmail);
        long task = jdbc.queryForObject("INSERT INTO tasks (project_id, title, created_by) VALUES (?, 'Task', ?) RETURNING id",
                Long.class, id, userId(jdbc, managerEmail));
        jdbc.update("INSERT INTO task_assignees (task_id, project_id, user_id) VALUES (?, ?, ?)", task, id, memberId);

        mvc.perform(delete("/projects/" + id + "/members/" + memberId).session(manager).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_assignees WHERE task_id = ?", Integer.class, task)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id = ?", Integer.class, task)).isEqualTo(1);
        MockHttpSession removed = login(mvc, memberEmail, PASSWORD);
        mvc.perform(get("/projects/" + id).session(removed)).andExpect(status().isNotFound()); // D18
    }

    @Test
    void adminSeesProjectsAndMembersButCannotChangeThem() throws Exception { // D11
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "visible"), PASSWORD);
        long id = createdId(createProject(manager, "Seen by admin", null));
        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        MockHttpSession admin = login(mvc, "admin@example.test", "test-admin-password");

        mvc.perform(get("/projects").param("q", "Seen by admin").session(admin))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].myRole").isEmpty())
                .andExpect(jsonPath("$.items[0].taskCounts").doesNotExist());
        mvc.perform(get("/projects/" + id + "/members").session(admin)).andExpect(status().isOk());
        send(patch("/projects/" + id), admin, "{\"version\":0,\"name\":\"Admin edit\"}").andExpect(status().isForbidden());
        mvc.perform(delete("/projects/" + id).session(admin).with(csrf())).andExpect(status().isForbidden());
    }

    private ResultActions createProject(MockHttpSession session, String name, String description) throws Exception {
        String body = json.writeValueAsString(description == null
                ? java.util.Map.of("name", name) : java.util.Map.of("name", name, "description", description));
        return send(post("/projects"), session, body);
    }

    private ResultActions addMember(MockHttpSession session, long projectId, String email, String role) throws Exception {
        String body = role == null ? "{\"email\":\"" + email + "\"}"
                : "{\"email\":\"" + email + "\",\"projectRole\":\"" + role + "\"}";
        return send(post("/projects/" + projectId + "/members"), session, body);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createdId(ResultActions result) throws Exception {
        return body(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}

package pt.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/** Assignees and comments over HTTP (US4, US7, FR8, FR11, D21, D22, A14, E1) and their effect on "last change". */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class AssigneesAndCommentsApiTest {

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
    long taskId;

    @BeforeEach
    void projectWithATask() throws Exception {
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "owner");
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "helper");
        manager = login(mvc, managerEmail, PASSWORD);
        member = login(mvc, memberEmail, PASSWORD);
        managerId = userId(jdbc, managerEmail);
        memberId = userId(jdbc, memberEmail);
        projectId = id(send(post("/projects"), manager, "{\"name\":\"Collaboration\"}"));
        send(post("/projects/" + projectId + "/members"), manager, "{\"email\":\"" + memberEmail + "\"}")
                .andExpect(status().isCreated());
        taskId = id(send(post("/projects/" + projectId + "/tasks"), manager, "{\"title\":\"Shared work\"}"));
    }

    @Test
    void severalAssigneesWithoutDuplicatesAndRemovableOneByOne() throws Exception { // US4, D21
        assign(member, managerId).andExpect(status().isCreated()).andExpect(jsonPath("$.assignees", hasSize(1)));
        assign(member, memberId).andExpect(status().isCreated()).andExpect(jsonPath("$.assignees", hasSize(2)));
        assign(manager, memberId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"));

        mvc.perform(delete("/tasks/" + taskId + "/assignees/" + managerId).session(member).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/tasks/" + taskId + "/assignees/" + managerId).session(member).with(csrf()))
                .andExpect(status().isNotFound());

        // still there when the task is opened again
        mvc.perform(get("/tasks/" + taskId).session(manager))
                .andExpect(jsonPath("$.assignees", hasSize(1)))
                .andExpect(jsonPath("$.assignees[0].id").value(memberId))
                .andExpect(jsonPath("$.version").value(0)); // assignee operations do not change the version
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE entity_type = 'ASSIGNEE' "
                + "AND changes ->> 'taskId' = ?", Integer.class, String.valueOf(taskId))).isEqualTo(3);
    }

    @Test
    void onlyEligibleUsersCanBeAssigned() throws Exception { // FR8, D22
        long outsider = userId(jdbc, createUser(jdbc, encoder, "MEMBER", "outsider"));
        assign(manager, outsider).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("userId"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_A_PROJECT_MEMBER"));
        assign(manager, 999_999L).andExpect(status().isUnprocessableEntity());
        send(post("/tasks/" + taskId + "/assignees"), manager, "{}").andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/projects/" + projectId + "/members").session(member))
                .andExpect(jsonPath("$", hasSize(2))); // the eligible users
    }

    @Test
    void commentsKeepAuthorAndDateFromTheServer() throws Exception { // US7, FR11
        send(post("/tasks/" + taskId + "/comments"), member, "{\"body\":\"  I will start tomorrow.  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("I will start tomorrow."))
                .andExpect(jsonPath("$.author.id").value(memberId))
                .andExpect(jsonPath("$.createdAt").exists());
        send(post("/tasks/" + taskId + "/comments"), manager, "{\"body\":\"Thanks!\"}").andExpect(status().isCreated());
        send(post("/tasks/" + taskId + "/comments"), manager, "{\"body\":\"Hi\",\"authorId\":" + memberId + "}")
                .andExpect(status().isBadRequest()); // the author cannot be sent

        mvc.perform(get("/tasks/" + taskId + "/comments").session(manager))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].author.id").value(memberId)) // oldest first
                .andExpect(jsonPath("$[1].body").value("Thanks!"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE entity_type = 'COMMENT' "
                + "AND changes::text LIKE '%tomorrow%'", Integer.class)).isZero(); // text not copied to the log
    }

    @Test
    void refusesEmptyOrTooLongComments() throws Exception { // D20
        for (String body : new String[] {"\"\"", "\"     \"", "null"}) {
            send(post("/tasks/" + taskId + "/comments"), member, "{\"body\":" + body + "}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].field").value("body"));
        }
        send(post("/tasks/" + taskId + "/comments"), member, "{\"body\":\"" + "c".repeat(2001) + "\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE task_id = ?", Integer.class, taskId)).isZero();
    }

    @Test
    void outsidersAndTheAdminCannotTouchAssigneesOrComments() throws Exception { // E4, D11, D17
        MockHttpSession outsider = login(mvc, createUser(jdbc, encoder, "MEMBER", "intruder"), PASSWORD);
        assign(outsider, memberId).andExpect(status().isNotFound());
        mvc.perform(get("/tasks/" + taskId + "/comments").session(outsider)).andExpect(status().isNotFound());
        send(post("/tasks/" + taskId + "/comments"), outsider, "{\"body\":\"Hello\"}").andExpect(status().isNotFound());

        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        MockHttpSession admin = login(mvc, "admin@example.test", "test-admin-password");
        mvc.perform(get("/tasks/" + taskId + "/comments").session(admin)).andExpect(status().isForbidden());
    }

    @Test
    void assigneeChangesCountAsTheLastChange() throws Exception { // FR14 (extra)
        assign(member, memberId).andExpect(status().isCreated());
        mvc.perform(get("/tasks/" + taskId).session(manager))
                .andExpect(jsonPath("$.lastChange.changedBy.id").value(memberId))
                .andExpect(jsonPath("$.lastChange.changes[0].field").value("assignees"));
        send(post("/tasks/" + taskId + "/comments"), manager, "{\"body\":\"Comments do not count\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/tasks/" + taskId).session(manager))
                .andExpect(jsonPath("$.lastChange.changedBy.id").value(memberId));
    }

    private ResultActions assign(MockHttpSession session, long userId) throws Exception {
        return send(post("/tasks/" + taskId + "/assignees"), session, "{\"userId\":" + userId + "}");
    }

    private long id(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

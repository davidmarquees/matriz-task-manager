package pt.matriz.web;

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
 * Regression tests for finding Q-6 (docs/quality-security-review.md): a NUL character (U+0000) never gets a 500
 * and nothing is saved. In a text field it is a control character, refused with 422 INVALID_CHARACTER (D49,
 * Worksheet 05); in other inputs (search, login and reset email) it is a malformed request (400, "BadRequest").
 * The JSON bodies below contain JSON escape sequences such as \u0000, as a client would send them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class NulCharacterIT {

    private static final String NUL = "\\u0000"; // JSON escape for U+0000

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
    long projectId;

    @BeforeEach
    void managerWithAProject() throws Exception {
        manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "nul-lead"), PASSWORD);
        projectId = json.readTree(send(post("/projects"), manager, "{\"name\":\"NUL project\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void aNulCharacterInATaskOrProjectTextIsRefusedAsAnInvalidCharacter() throws Exception {
        expectInvalidCharacter(send(post("/projects/" + projectId + "/tasks"), manager, "{\"title\":\"ab" + NUL + "cd\"}"), "title");
        expectInvalidCharacter(send(post("/projects/" + projectId + "/tasks"), manager, "{\"title\":\"" + NUL + "abcd\"}"), "title");
        expectInvalidCharacter(send(post("/projects"), manager, "{\"name\":\"ab" + NUL + "cd\"}"), "name");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE project_id = ?", Integer.class, projectId))
                .isZero();

        long taskId = json.readTree(send(post("/projects/" + projectId + "/tasks"), manager, "{\"title\":\"Original\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        expectInvalidCharacter(send(patch("/tasks/" + taskId), manager, "{\"version\":0,\"title\":\"ab" + NUL + "cd\"}"), "title");
        assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id = ?", String.class, taskId)).isEqualTo("Original");
    }

    @Test
    void otherControlCharactersFollowTheSingleAndMultiLineRules() throws Exception { // T32, D49
        String tasks = "/projects/" + projectId + "/tasks";
        expectInvalidCharacter(send(post(tasks), manager, "{\"title\":\"ab\\u0007cd\"}"), "title");
        expectInvalidCharacter(send(post(tasks), manager, "{\"title\":\"Line\\nbreak\"}"), "title");
        expectInvalidCharacter(send(post(tasks), manager, "{\"title\":\"Valid\",\"description\":\"a\\u001Bb\"}"), "description");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE project_id = ?", Integer.class, projectId))
                .isZero();

        long taskId = json.readTree(send(post(tasks), manager, "{\"title\":\"Valid\",\"description\":\"line 1\\n\\tline 2\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT description FROM tasks WHERE id = ?", String.class, taskId))
                .isEqualTo("line 1\n\tline 2");
        send(post("/tasks/" + taskId + "/comments"), manager, "{\"body\":\"first\\r\\nsecond\"}")
                .andExpect(status().isCreated());
        expectInvalidCharacter(send(post("/tasks/" + taskId + "/comments"), manager, "{\"body\":\"a\\u0007b\"}"), "body");
    }

    @Test
    void aNulCharacterInASearchIsAMalformedRequest() throws Exception {
        expect400(mvc.perform(get("/projects/" + projectId + "/tasks").param("q", "a\u0000b").session(manager)));
        expect400(mvc.perform(get("/projects").param("q", "a\u0000b").session(manager)));
    }

    @Test
    void aNulCharacterInLoginOrResetEmailIsAMalformedRequest() throws Exception {
        expect400(mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a" + NUL + "@example.test\",\"password\":\"x\"}")));
        expect400(mvc.perform(post("/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a" + NUL + "@example.test\"}")));
    }

    private void expect400(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    private void expectInvalidCharacter(ResultActions result, String field) throws Exception {
        result.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CHARACTER"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

package pt.matriz.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/** Reading the project activity log (FR13, US12, A23, D41, D42). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class ActivityApiIT {

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

    @Test
    void managersSeeWhoChangedWhatAndWhenNewestFirst() throws Exception { // US12
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "lead"), PASSWORD);
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "dev");
        MockHttpSession member = login(mvc, memberEmail, PASSWORD);
        long project = id(send(post("/projects"), manager, "{\"name\":\"Audited\"}"));
        send(post("/projects/" + project + "/members"), manager, "{\"email\":\"" + memberEmail + "\"}");
        long task = id(send(post("/projects/" + project + "/tasks"), member, "{\"title\":\"Fix login\"}"));
        send(patch("/tasks/" + task), member, "{\"version\":0,\"status\":\"DONE\"}");
        send(post("/tasks/" + task + "/comments"), member, "{\"body\":\"Secret comment text\"}");
        long gone = id(send(post("/projects/" + project + "/tasks"), manager, "{\"title\":\"Short-lived\"}"));
        mvc.perform(delete("/tasks/" + gone).session(manager).with(csrf())).andExpect(status().isNoContent());

        JsonNode page = body(mvc.perform(get("/projects/" + project + "/activity").session(manager)).andExpect(status().isOk()));
        JsonNode items = page.get("items");

        assertThat(page.get("totalItems").asInt()).isEqualTo(items.size());
        assertThat(items.get(0).get("entityType").asText()).isEqualTo("TASK");
        assertThat(items.get(0).get("action").asText()).isEqualTo("DELETED");
        assertThat(items.get(0).get("entityTitle").asText()).isEqualTo("Short-lived"); // kept after delete
        JsonNode statusChange = find(items, "TASK", "UPDATED");
        assertThat(statusChange.get("actor").get("id").asLong()).isEqualTo(userId(jdbc, memberEmail));
        assertThat(statusChange.get("entityTitle").asText()).isEqualTo("Fix login");
        assertThat(statusChange.get("changes").get("status").get("old").asText()).isEqualTo("TODO");
        assertThat(statusChange.get("changes").get("status").get("new").asText()).isEqualTo("DONE");
        assertThat(find(items, "MEMBER", "ADDED").get("entityUser").get("id").asLong()).isEqualTo(userId(jdbc, memberEmail));
        assertThat(find(items, "COMMENT", "CREATED").get("entityTitle").asText()).isEqualTo("Fix login");
        assertThat(page.toString()).doesNotContain("Secret comment text");
    }

    @Test
    void onlyManagersCanReadIt() throws Exception { // A23, D41, D42, D11
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "boss"), PASSWORD);
        String memberEmail = createUser(jdbc, encoder, "MEMBER", "worker");
        long project = id(send(post("/projects"), manager, "{\"name\":\"Private log\"}"));
        send(post("/projects/" + project + "/members"), manager, "{\"email\":\"" + memberEmail + "\"}");

        mvc.perform(get("/projects/" + project + "/activity").session(login(mvc, memberEmail, PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/projects/" + project + "/activity").session(login(mvc, createUser(jdbc, encoder, "MANAGER", "other"), PASSWORD)))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        mvc.perform(get("/projects/" + project + "/activity").session(login(mvc, "admin@example.test", "test-admin-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/projects/" + project + "/activity").param("size", "500").session(manager))
                .andExpect(status().isUnprocessableEntity());
    }

    private JsonNode find(JsonNode items, String type, String action) {
        for (JsonNode item : items) {
            if (item.get("entityType").asText().equals(type) && item.get("action").asText().equals(action)) {
                return item;
            }
        }
        throw new AssertionError("No " + type + " " + action + " entry in " + items);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(ResultActions result) throws Exception {
        return body(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}

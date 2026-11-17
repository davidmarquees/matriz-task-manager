package pt.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.PostgresTestDatabase;

/** Account administration (US9, US10, US12, FR1a, FR1b, FR1d, D1 to D5, D12, D14, D19, D38, D44). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class AdminApiIT {

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

    MockHttpSession admin;

    @BeforeEach
    void adminSession() throws Exception {
        jdbc.update("UPDATE users SET must_change_password = false WHERE account_role = 'ADMIN'");
        admin = login(mvc, "admin@example.test", "test-admin-password");
    }

    @Test
    void adminCreatesAMemberWhoMustChangeThePasswordAndCannotCreateProjects() throws Exception { // US9
        long id = id(send(post("/admin/users"), admin,
                "{\"name\":\"Rita Nova\",\"email\":\"rita.nova@example.test\",\"accountRole\":\"MEMBER\",\"initialPassword\":\"initial-pass\"}"));

        MockHttpSession rita = login(mvc, "rita.nova@example.test", "initial-pass");
        mvc.perform(get("/auth/me").session(rita)).andExpect(jsonPath("$.mustChangePassword").value(true));
        send(post("/auth/password"), rita, "{\"currentPassword\":\"initial-pass\",\"newPassword\":\"ritas-own-pass\"}")
                .andExpect(status().isNoContent());
        send(post("/projects"), rita, "{\"name\":\"Not allowed\"}").andExpect(status().isForbidden());

        send(post("/admin/users"), admin, "{\"name\":\"Copy\",\"email\":\"RITA.NOVA@example.test\",\"accountRole\":\"MEMBER\","
                + "\"initialPassword\":\"initial-pass\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        send(post("/admin/users"), admin, "{\"name\":\"Short\",\"email\":\"short@example.test\",\"accountRole\":\"MEMBER\","
                + "\"initialPassword\":\"short\"}").andExpect(status().isUnprocessableEntity());
        send(post("/admin/users"), admin, "{\"name\":\"Boss\",\"email\":\"boss@example.test\",\"accountRole\":\"ADMIN\","
                + "\"initialPassword\":\"initial-pass\"}").andExpect(status().isUnprocessableEntity());
        assertThat(audit(id, "ACCOUNT_CREATED")).isEqualTo(1);
    }

    @Test
    void onlyTheAdminManagesAccounts() throws Exception { // D2, D38
        MockHttpSession manager = login(mvc, createUser(jdbc, encoder, "MANAGER", "notadmin"), PASSWORD);
        mvc.perform(get("/admin/users").session(manager)).andExpect(status().isForbidden());
        send(post("/admin/users"), manager, "{\"name\":\"X\",\"email\":\"x@example.test\",\"accountRole\":\"MEMBER\","
                + "\"initialPassword\":\"initial-pass\"}").andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").session(manager)).andExpect(status().isForbidden());
    }

    @Test
    void deactivationRemovesAccessAndMembershipsReactivationStartsClean() throws Exception { // US10, D8, D19, E1
        String managerEmail = createUser(jdbc, encoder, "MANAGER", "lead");
        String leaverEmail = createUser(jdbc, encoder, "MEMBER", "leaver");
        MockHttpSession manager = login(mvc, managerEmail, PASSWORD);
        long project = id(send(post("/projects"), manager, "{\"name\":\"Team work\"}"));
        send(post("/projects/" + project + "/members"), manager, "{\"email\":\"" + leaverEmail + "\"}");
        long task = id(send(post("/projects/" + project + "/tasks"), manager,
                "{\"title\":\"Shared\",\"assigneeIds\":[" + userId(jdbc, leaverEmail) + "]}"));
        MockHttpSession leaver = login(mvc, leaverEmail, PASSWORD);
        long leaverId = userId(jdbc, leaverEmail);

        send(post("/admin/users/" + leaverId + "/deactivate"), admin, "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"));

        mvc.perform(get("/auth/me").session(leaver)).andExpect(status().isUnauthorized()); // open session ends
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE user_id = ?", Integer.class, leaverId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM task_assignees WHERE task_id = ?", Integer.class, task)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE id = ?", Integer.class, task)).isEqualTo(1);
        send(post("/admin/users/" + leaverId + "/deactivate"), admin, "{}").andExpect(status().isConflict()); // D3

        send(post("/admin/users/" + leaverId + "/reactivate"), admin, "{\"initialPassword\":\"welcome-back\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        MockHttpSession back = login(mvc, leaverEmail, "welcome-back");
        send(post("/auth/password"), back, "{\"currentPassword\":\"welcome-back\",\"newPassword\":\"my-new-pass\"}");
        mvc.perform(get("/projects").session(back)).andExpect(jsonPath("$.totalItems").value(0)); // D19
    }

    @Test
    void theOnlyManagerCannotBeDeactivatedUntilAnotherIsAppointed() throws Exception { // D5, D12
        String soloEmail = createUser(jdbc, encoder, "MANAGER", "solo");
        MockHttpSession solo = login(mvc, soloEmail, PASSWORD);
        long project = id(send(post("/projects"), solo, "{\"name\":\"Solo project\"}"));
        String deputyEmail = createUser(jdbc, encoder, "MEMBER", "deputy");

        send(post("/admin/users/" + userId(jdbc, soloEmail) + "/deactivate"), admin, "{}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ONLY_MANAGER"));

        send(post("/projects/" + project + "/managers"), admin, "{\"email\":\"" + deputyEmail + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.projectRole").value("MANAGER"));
        send(post("/projects/" + project + "/managers"), admin, "{\"email\":\"admin@example.test\"}")
                .andExpect(status().isUnprocessableEntity()); // D14
        send(post("/admin/users/" + userId(jdbc, soloEmail) + "/deactivate"), admin, "{}").andExpect(status().isOk());
        assertThat(audit(userId(jdbc, deputyEmail), "MANAGER_APPOINTED")).isEqualTo(1);
    }

    @Test
    void anonymisationOnlyAfterDeactivationAndIsFinal() throws Exception { // D44, GDPR
        String goneEmail = createUser(jdbc, encoder, "MEMBER", "gdpr");
        long id = userId(jdbc, goneEmail);

        send(post("/admin/users/" + id + "/anonymise"), admin, "{}").andExpect(status().isConflict());
        send(post("/admin/users/" + id + "/deactivate"), admin, "{}").andExpect(status().isOk());
        send(post("/admin/users/" + id + "/anonymise"), admin, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANONYMISED"))
                .andExpect(jsonPath("$.email").value("removed-" + id + "@invalid"));
        assertThat(jdbc.queryForObject("SELECT name IS NULL AND password_hash IS NULL FROM users WHERE id = ?", Boolean.class, id))
                .isTrue();
        send(post("/admin/users/" + id + "/reactivate"), admin, "{\"initialPassword\":\"initial-pass\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANONYMISED"));
    }

    @Test
    void searchTextLongerThanTheContractAllowsIsRefused() throws Exception { // openapi.yaml maxLength 100 (Ex. 9 R-3)
        mvc.perform(get("/admin/users").param("q", "x".repeat(101)).session(admin))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        mvc.perform(get("/admin/users").param("q", "x".repeat(100)).session(admin)).andExpect(status().isOk());
    }

    @Test
    void theAdminCannotDeactivateTheirOwnAccount() throws Exception { // D4
        long adminId = userId(jdbc, "admin@example.test");
        send(post("/admin/users/" + adminId + "/deactivate"), admin, "{}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void theAuditLogShowsWhoDidWhatWithoutPersonalValues() throws Exception { // US12, FR1d
        String email = createUser(jdbc, encoder, "MEMBER", "audited");
        long id = userId(jdbc, email);
        send(post("/admin/users/" + id + "/deactivate"), admin, "{}");

        String body = mvc.perform(get("/admin/audit").param("userId", String.valueOf(id)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("ACCOUNT_DEACTIVATED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(userId(jdbc, "admin@example.test")))
                .andExpect(jsonPath("$.items[0].targetUser.id").value(id))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(email.split("@")[0] + "@").doesNotContain("password_hash");
    }

    private int audit(long target, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM user_audit_log WHERE target_user_id = ? AND action = ?",
                Integer.class, target, action);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               MockHttpSession session, String body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }
}

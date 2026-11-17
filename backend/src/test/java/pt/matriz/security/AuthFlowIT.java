package pt.matriz.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.PostgresTestDatabase;

/**
 * Authentication and session rules (US1, US11, D6 to D8, D45 to D48), through the real HTTP layer
 * and security filters, against the demo data of the dev profile. Users created here have unique
 * emails, so tests do not affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class AuthFlowIT {

    static final String DEMO_PASSWORD = "test-demo-password";

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
        registry.add("matriz.login.rate-limit.max-attempts", () -> "1000"); // tested in LoginRateLimitIT
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void validLoginStartsASessionAndMeReturnsTheUser() throws Exception {
        MockHttpSession session = login("member@example.test", DEMO_PASSWORD);

        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("member@example.test"))
                .andExpect(jsonPath("$.accountRole").value("MEMBER"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception { // D6
        String wrongPassword = mvc.perform(loginRequest("member@example.test", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(loginRequest("nobody@example.test", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("Invalid email or password.");
    }

    @Test
    void requestWithoutSessionIs401() throws Exception { // US1, D8
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")));
        // The cookies themselves are checked over real HTTP in SessionCookiesIT.
    }

    @Test
    void loginWithoutCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("member@example.test", DEMO_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception { // D7, A17
        String email = createUser("lock@example.test", "correct-password", false);
        for (int i = 0; i < 5; i++) {
            mvc.perform(loginRequest(email, "wrong-password")).andExpect(status().isUnauthorized());
        }
        String locked = mvc.perform(loginRequest(email, "correct-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(locked).contains("Invalid email or password."); // same answer, does not say "locked"
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ? AND locked_until > now()",
                Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_log a JOIN users u ON u.id = a.target_user_id "
                + "WHERE u.email = ? AND a.action = 'ACCOUNT_LOCKED'", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception { // D7
        String email = createUser("reset@example.test", "correct-password", false);
        for (int i = 0; i < 4; i++) {
            mvc.perform(loginRequest(email, "wrong-password")).andExpect(status().isUnauthorized());
        }
        login(email, "correct-password");
        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void deactivatedAccountCannotLogInAndItsOpenSessionStopsWorking() throws Exception { // D6, D8
        String email = createUser("leaver@example.test", "correct-password", false);
        MockHttpSession session = login(email, "correct-password");

        jdbc.update("UPDATE users SET status = 'DEACTIVATED', deactivated_at = now() WHERE email = ?", email);

        mvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(loginRequest(email, "correct-password")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login("manager@example.test", DEMO_PASSWORD);

        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRequiredBlocksEverythingElse() throws Exception { // D45
        String email = createUser("first@example.test", "initial-password", true);
        MockHttpSession session = login(email, "initial-password");

        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/projects").session(session)).andExpect(status().isForbidden());

        changePassword(session, "initial-password", "a-brand-new-password").andExpect(status().isNoContent());

        mvc.perform(get("/projects").session(session)).andExpect(status().isOk()); // no longer 403
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE email = ?", Boolean.class, email))
                .isFalse();
    }

    @Test
    void passwordChangeRefusesWrongCurrentSameOrShortPassword() throws Exception { // D46, D47
        String email = createUser("rules@example.test", "current-password", false);
        MockHttpSession session = login(email, "current-password");

        changePassword(session, "not-the-current", "another-password")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("WRONG_PASSWORD"));
        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, email))
                .isEqualTo(1); // counts as a failed login attempt (D46)

        changePassword(session, "current-password", "current-password")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("SAME_AS_CURRENT"));

        changePassword(session, "current-password", "short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_SHORT"));
    }

    @Test
    void passwordChangeEndsTheOtherSessionsAndRecordsIt() throws Exception { // D48
        String email = createUser("sessions@example.test", "current-password", false);
        MockHttpSession laptop = login(email, "current-password");
        MockHttpSession phone = login(email, "current-password");
        String laptopIdBefore = laptop.getId();

        changePassword(laptop, "current-password", "a-brand-new-password").andExpect(status().isNoContent());

        assertThat(laptop.getId()).isNotEqualTo(laptopIdBefore);
        mvc.perform(get("/auth/me").session(laptop)).andExpect(status().isOk());
        mvc.perform(get("/auth/me").session(phone)).andExpect(status().isUnauthorized());
        login(email, "a-brand-new-password");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_log a JOIN users u ON u.id = a.target_user_id "
                + "WHERE u.email = ? AND a.action = 'PASSWORD_CHANGED' AND a.actor_id = u.id", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void preferencesAreSavedInTheProfileAndAudited() throws Exception { // NFR1, NFR8, US13
        String email = createUser("prefs@example.test", "current-password", false);
        MockHttpSession session = login(email, "current-password");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/auth/me/preferences")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"en\",\"theme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.theme").value("DARK"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/auth/me/preferences")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"PURPLE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("theme"));

        // another device: the saved choice comes with the user
        MockHttpSession otherDevice = login(email, "current-password");
        mvc.perform(get("/auth/me").session(otherDevice))
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.theme").value("DARK"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_log a JOIN users u ON u.id = a.target_user_id "
                + "WHERE u.email = ? AND a.action = 'PREFERENCES_CHANGED'", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void malformedRequestsGetTheContractStatusCodes() throws Exception {
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.test\",\"password\":\"x\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest()); // unknown field
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.test\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(loginRequest(email, password)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(MockHttpSession session, String current,
                                                                              String next) throws Exception {
        return mvc.perform(post("/auth/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private org.springframework.test.web.servlet.RequestBuilder loginRequest(String email, String password) {
        return post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(email, password));
    }

    private static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String createUser(String email, String password, boolean mustChangePassword) {
        jdbc.update("INSERT INTO users (name, email, password_hash, must_change_password, account_role) "
                + "VALUES ('Test User', ?, ?, ?, 'MEMBER')", email, encoder.encode(password), mustChangePassword);
        return email;
    }
}

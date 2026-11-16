package pt.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.matriz.ApiTestSupport.PASSWORD;
import static pt.matriz.ApiTestSupport.createUser;
import static pt.matriz.ApiTestSupport.login;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.PostgresTestDatabase;
import pt.matriz.email.EmailSender;

/** Password reset by email (US11, FR1c, D30 to D36). The email adapter is replaced by a mock that records the link. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class PasswordResetTest {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
        registry.add("matriz.login.rate-limit.max-attempts", () -> "10000");
        registry.add("matriz.app-url", () -> "http://app.example.test");
    }

    @MockitoBean
    EmailSender email;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @AfterEach
    void clear() {
        clearInvocations(email);
    }

    @Test
    void theEmailedLinkSetsANewPasswordOnceAndEndsOtherSessions() throws Exception { // D34
        String user = createUser(jdbc, encoder, "MEMBER", "forgetful");
        MockHttpSession oldSession = login(mvc, user, PASSWORD);

        request(user).andExpect(status().isAccepted());
        String token = sentToken(user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, token))
                .isZero(); // only the hash is stored (NFR3)

        confirm(token, "a-brand-new-pass").andExpect(status().isNoContent());

        mvc.perform(get("/auth/me").session(oldSession)).andExpect(status().isUnauthorized());
        login(mvc, user, "a-brand-new-pass");
        confirm(token, "another-new-pass").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TOKEN")); // D32 used
    }

    @Test
    void unknownEmailsGetTheSameAnswerAndNoEmail() throws Exception { // D30
        request("nobody@example.test").andExpect(status().isAccepted());
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void expiredLinksAndShortPasswordsAreRefused() throws Exception { // D32, D35
        String user = createUser(jdbc, encoder, "MEMBER", "slow");
        request(user);
        String token = sentToken(user);

        confirm(token, "short").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].code").value("TOO_SHORT"));
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");
        confirm(token, "long-enough-pass").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TOKEN"));
    }

    @Test
    void aNewRequestInvalidatesOlderLinksAndTheHourlyLimitStopsEmails() throws Exception { // D31, D33
        String user = createUser(jdbc, encoder, "MEMBER", "eager");
        request(user);
        String first = sentToken(user);
        clearInvocations(email);
        request(user);
        String second = sentToken(user);
        request(user);
        clearInvocations(email);
        request(user).andExpect(status().isAccepted()); // 4th in the hour

        verify(email, never()).send(eq(user), anyString(), anyString());
        confirm(first, "first-link-pass").andExpect(status().isUnprocessableEntity());
        confirm(second, "second-link-pass").andExpect(status().isUnprocessableEntity()); // replaced by the 3rd
    }

    @Test
    void aMailServerFailureGivesTheSameAnswer() throws Exception { // D36
        String user = createUser(jdbc, encoder, "MEMBER", "offline");
        doThrow(new IllegalStateException("SMTP down")).when(email).send(anyString(), anyString(), anyString());
        request(user).andExpect(status().isAccepted());
    }

    private String sentToken(String to) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email, times(1)).send(eq(to), anyString(), body.capture());
        String text = body.getValue();
        assertThat(text).contains("http://app.example.test/reset-password?token=");
        return text.substring(text.indexOf("token=") + 6).split("\\s")[0];
    }

    private ResultActions request(String emailAddress) throws Exception {
        return mvc.perform(post("/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + emailAddress + "\"}"));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mvc.perform(post("/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }
}

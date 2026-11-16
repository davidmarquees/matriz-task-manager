package pt.matriz;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Helpers for API tests: real login through /auth/login and users with unique fictitious emails. */
public final class ApiTestSupport {

    public static final String PASSWORD = "test-user-password";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ApiTestSupport() {
    }

    public static MockHttpSession login(MockMvc mvc, String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Creates an active account (no forced password change) and returns its email. */
    public static String createUser(JdbcTemplate jdbc, PasswordEncoder encoder, String role, String prefix) {
        String email = prefix + "-" + SEQUENCE.incrementAndGet() + "@example.test";
        jdbc.update("INSERT INTO users (name, email, password_hash, must_change_password, account_role) "
                + "VALUES (?, ?, ?, false, ?)", "Test " + prefix, email, encoder.encode(PASSWORD), role);
        return email;
    }

    public static long userId(JdbcTemplate jdbc, String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}

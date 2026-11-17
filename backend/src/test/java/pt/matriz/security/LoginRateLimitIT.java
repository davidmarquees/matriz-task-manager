package pt.matriz.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.PostgresTestDatabase;

/** Login rate limit per IP address (increment 2): with a limit of 3, the 4th attempt gets 429. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class LoginRateLimitIT {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
        registry.add("matriz.login.rate-limit.max-attempts", () -> "3");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void blocksTheIpAddressAfterTheLimitButNotOtherAddresses() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(login("10.0.0.1", "wrong-password")).andExpect(status().isUnauthorized());
        }
        // even the correct password is refused while the limit lasts
        mvc.perform(login("10.0.0.1", "test-demo-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        mvc.perform(login("10.0.0.2", "test-demo-password")).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder login(String ip, String password) {
        return post("/auth/login").with(csrf()).with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.test\",\"password\":\"" + password + "\"}");
    }
}

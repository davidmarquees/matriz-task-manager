package pt.matriz.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import pt.matriz.PostgresTestDatabase;

/**
 * Session expiry after inactivity (A18, D8) over real HTTP. The configured timeout is checked to be
 * 30 minutes; to test the behaviour without waiting 30 minutes, sessions created in this test expire
 * after 1 second of inactivity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
@Import(SessionTimeoutIT.ShortSessions.class)
class SessionTimeoutIT {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
    }

    @TestConfiguration
    static class ShortSessions {
        @Bean
        HttpSessionListener oneSecondSessions() {
            return new HttpSessionListener() {
                @Override
                public void sessionCreated(HttpSessionEvent event) {
                    event.getSession().setMaxInactiveInterval(1);
                }
            };
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    Environment environment;

    @Test
    void theConfiguredInactivityTimeoutIs30Minutes() {
        assertThat(environment.getProperty("server.servlet.session.timeout", Duration.class))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void aSessionIdleLongerThanTheTimeoutIsRefused() throws Exception {
        ResponseEntity<String> anonymous = http.getForEntity("/auth/me", String.class);
        String csrf = value(cookie(anonymous, "XSRF-TOKEN"));
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrf);
        loginHeaders.add("X-XSRF-TOKEN", csrf);
        ResponseEntity<String> login = http.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(
                "{\"email\":\"member@example.test\",\"password\":\"test-demo-password\"}", loginHeaders), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders session = new HttpHeaders();
        session.add(HttpHeaders.COOKIE, "JSESSIONID=" + value(cookie(login, "JSESSIONID")));

        assertThat(http.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(session), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK); // active

        Thread.sleep(2500); // longer than the inactivity timeout of this test

        assertThat(http.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(session), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED); // expired: the interface goes to login
    }

    private static String cookie(ResponseEntity<?> response, String name) {
        List<String> cookies = response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        return cookies.stream().filter(c -> c.startsWith(name + "=")).reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in " + cookies));
    }

    private static String value(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }
}

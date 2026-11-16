package pt.matriz.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import pt.matriz.PostgresTestDatabase;

/**
 * The session lifecycle of openapi.yaml over real HTTP, as the SPA does it: read the XSRF-TOKEN cookie,
 * log in with it, use the session cookie, log out. Also checks the cookie attributes (SameSite=Lax,
 * HttpOnly session cookie).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
class SessionCookiesTest {

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void sessionLifecycleWithCookies() {
        // 1. No session: 401, and the CSRF cookie readable by the SPA on every page
        ResponseEntity<String> anonymous = http.getForEntity("/auth/me", String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String csrfCookie = cookie(anonymous, "XSRF-TOKEN");
        assertThat(csrfCookie).contains("Path=/;").contains("SameSite=Lax").doesNotContain("HttpOnly");
        String csrfToken = value(csrfCookie);

        // 2. Login with the token: new HttpOnly session cookie and a new CSRF token
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken);
        loginHeaders.add("X-XSRF-TOKEN", csrfToken);
        ResponseEntity<String> login = http.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(
                "{\"email\":\"manager@example.test\",\"password\":\"test-demo-password\"}", loginHeaders), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String sessionCookie = cookie(login, "JSESSIONID");
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");
        String newCsrfToken = value(cookie(login, "XSRF-TOKEN"));
        assertThat(newCsrfToken).isNotBlank().isNotEqualTo(csrfToken);

        // 3. Authenticated request with the session cookie
        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.add(HttpHeaders.COOKIE, "JSESSIONID=" + value(sessionCookie) + "; XSRF-TOKEN=" + newCsrfToken);
        ResponseEntity<String> me = http.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(sessionHeaders), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("manager@example.test");

        // 4. Logout with the new token, then the session no longer works
        sessionHeaders.add("X-XSRF-TOKEN", newCsrfToken);
        ResponseEntity<String> logout = http.exchange("/auth/logout", HttpMethod.POST, new HttpEntity<>(sessionHeaders),
                String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> after = http.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(sessionHeaders),
                String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

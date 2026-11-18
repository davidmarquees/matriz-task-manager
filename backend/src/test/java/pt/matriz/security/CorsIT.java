package pt.matriz.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pt.matriz.PostgresTestDatabase;

/**
 * CORS (docs/deployment.md): only the origin in MATRIZ_CORS_ALLOWED_ORIGINS may call the API from
 * another site, with credentials; any other origin is refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("dev")
class CorsIT {

    static final String ALLOWED = "https://matriz-web.example.test";

    @Container
    static final PostgreSQLContainer<?> DB = PostgresTestDatabase.newContainer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, DB);
        registry.add("MATRIZ_CORS_ALLOWED_ORIGINS", () -> ALLOWED);
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void allowsThePreflightFromTheConfiguredOrigin() {
        ResponseEntity<String> response = preflight(ALLOWED);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED);
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    void refusesThePreflightFromAnyOtherOrigin() {
        ResponseEntity<String> response = preflight("https://evil.example.test");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    void sendsNoCorsHeaderToAnotherOriginOnAnOrdinaryRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://evil.example.test");
        ResponseEntity<String> response = http.exchange("/actuator/health", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private ResponseEntity<String> preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token");
        return http.exchange("/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }
}

package pt.matriz;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Isolated PostgreSQL database for integration tests (Testcontainers).
 *
 * <p>Each test class starts its own empty container, so tests never depend on earlier runs or on
 * the development database. The container uses the same init script as docker-compose.yml, so the
 * application runs as the restricted application user and Flyway as the owner, like in development.
 */
public final class PostgresTestDatabase {

    public static final String APP_USER = "matriz_app_test";
    public static final String APP_PASSWORD = "test-app-password";

    private PostgresTestDatabase() {
    }

    public static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("matriz_test")
                .withUsername("matriz_owner_test")
                .withPassword("test-owner-password")
                .withEnv("MATRIZ_DB_APP_USER", APP_USER)
                .withEnv("MATRIZ_DB_APP_PASSWORD", APP_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../docker/postgres/init/01-create-app-user.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-create-app-user.sh");
    }

    /** Points the application at the container; fictitious values only. */
    public static void register(DynamicPropertyRegistry registry, PostgreSQLContainer<?> db) {
        registry.add("MATRIZ_DB_URL", db::getJdbcUrl);
        registry.add("MATRIZ_DB_OWNER_USER", db::getUsername);
        registry.add("MATRIZ_DB_OWNER_PASSWORD", db::getPassword);
        registry.add("MATRIZ_DB_APP_USER", () -> APP_USER);
        registry.add("MATRIZ_DB_APP_PASSWORD", () -> APP_PASSWORD);
        registry.add("MATRIZ_ADMIN_NAME", () -> "Test Admin");
        registry.add("MATRIZ_ADMIN_EMAIL", () -> "admin@example.test");
        registry.add("MATRIZ_ADMIN_PASSWORD", () -> "test-admin-password");
        registry.add("MATRIZ_DEMO_PASSWORD", () -> "test-demo-password");
    }
}

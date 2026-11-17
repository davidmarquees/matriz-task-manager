package pt.matriz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point of the Matriz backend: one Spring Boot application split into business modules
 * (modular monolith, see docs/architecture.md, section 5).
 * Spring Boot's default in-memory user is turned off: login is only through /auth/login (accounts in
 * the database), and the default would print a generated password in the log.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MatrizApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatrizApplication.class, args);
    }
}

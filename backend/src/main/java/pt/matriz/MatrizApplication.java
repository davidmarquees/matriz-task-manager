package pt.matriz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Matriz backend: one Spring Boot application split into business modules
 * (modular monolith, see docs/architecture.md, section 5).
 */
@SpringBootApplication
public class MatrizApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatrizApplication.class, args);
    }
}

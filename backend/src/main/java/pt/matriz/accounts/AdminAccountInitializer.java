package pt.matriz.accounts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the Admin account at the first start (FR12, A25).
 *
 * <p>Runs on every start-up but does nothing when an Admin account already exists, so data is
 * never duplicated or overwritten. Name, email and password come from the MATRIZ_ADMIN_*
 * environment variables. The password is stored only as a BCrypt hash (NFR3), and the Admin must
 * change it at the first login (FR1e). The creation is recorded in the user audit log in the same
 * transaction (FR1d).
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 8; // A21

    private final JdbcTemplate jdbc;
    private final String name;
    private final String email;
    private final String password;

    public AdminAccountInitializer(JdbcTemplate jdbc,
                                   @Value("${matriz.admin.name}") String name,
                                   @Value("${matriz.admin.email}") String email,
                                   @Value("${matriz.admin.password}") String password) {
        this.jdbc = jdbc;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer admins = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE account_role = 'ADMIN'", Integer.class);
        if (admins != null && admins > 0) {
            return;
        }
        if (isBlank(name) || isBlank(email) || password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("No Admin account exists. Set MATRIZ_ADMIN_NAME, MATRIZ_ADMIN_EMAIL "
                    + "and MATRIZ_ADMIN_PASSWORD (at least " + MIN_PASSWORD_LENGTH + " characters) to create it.");
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO users (name, email, password_hash, must_change_password, account_role) "
                        + "VALUES (?, ?, ?, true, 'ADMIN') RETURNING id",
                Long.class, name.trim(), email.trim(), new BCryptPasswordEncoder().encode(password));
        jdbc.update("INSERT INTO user_audit_log (actor_id, target_user_id, action) VALUES (NULL, ?, 'ACCOUNT_CREATED')",
                id);
        // No personal data in the log (GDPR, docs/constraints.md section 3): only the id.
        log.info("Admin account created (user id {})", id);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

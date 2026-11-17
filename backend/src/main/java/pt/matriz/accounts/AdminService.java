package pt.matriz.accounts;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.audit.UserAuditLog;
import pt.matriz.projects.MemberService;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.web.ApiException;
import pt.matriz.web.TextInput;

/**
 * Account administration by the Admin (FR1a, FR1b, US9, US10): create, deactivate, reactivate and
 * anonymise accounts. Every change is written to the user audit log in the same transaction (FR1d).
 */
@Service
public class AdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Account as the Admin sees it (no password hash). */
    public record AdminAccount(long id, String name, String email, String accountRole, String status,
                               boolean mustChangePassword, OffsetDateTime createdAt, OffsetDateTime deactivatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final UserAuditLog audit;
    private final MemberService members;
    private final Clock clock;

    public AdminService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder, UserAuditLog audit,
                        MemberService members, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.members = members;
        this.clock = clock;
    }

    /** Every account, sorted by name; filtered by status and by a text in the name or email. */
    @Transactional(readOnly = true)
    public List<AdminAccount> list(AuthenticatedUser admin, String status, String q) {
        requireAdmin(admin);
        if (status != null && !status.isBlank() && !List.of("ACTIVE", "DEACTIVATED", "ANONYMISED").contains(status)) {
            throw ApiException.invalid("status", "INVALID_VALUE");
        }
        if (q != null && q.length() > 100) {
            throw ApiException.invalid("q", "TOO_LONG"); // openapi.yaml maxLength
        }
        String pattern = q == null || q.isBlank() ? null
                : "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("""
                SELECT id, name, email, account_role, status, must_change_password, created_at, deactivated_at
                FROM users
                WHERE (CAST(? AS varchar) IS NULL OR status = ?)
                  AND (CAST(? AS varchar) IS NULL OR name ILIKE ? ESCAPE '\\' OR email ILIKE ? ESCAPE '\\')
                ORDER BY lower(coalesce(name, '')), id""",
                (rs, i) -> new AdminAccount(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("account_role"), rs.getString("status"), rs.getBoolean("must_change_password"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("deactivated_at", OffsetDateTime.class)),
                blankToNull(status), blankToNull(status), pattern, pattern, pattern);
    }

    /** New account with an initial password that must be changed at the first login (FR1a, FR1e, D1). */
    @Transactional
    public AdminAccount create(AuthenticatedUser admin, String name, String email, String accountRole, String password) {
        requireAdmin(admin);
        String cleanName = TextInput.required(name, "name", 1, 100);
        String cleanEmail = TextInput.required(email, "email", 3, 254);
        if (!EMAIL.matcher(cleanEmail).matches()) {
            throw ApiException.invalid("email", "INVALID_VALUE");
        }
        if (accountRole == null) {
            throw ApiException.invalid("accountRole", "REQUIRED");
        }
        if (!accountRole.equals("MANAGER") && !accountRole.equals("MEMBER")) {
            throw ApiException.invalid("accountRole", "INVALID_VALUE");
        }
        checkPassword(password, "initialPassword");
        long id;
        try {
            id = jdbc.queryForObject("INSERT INTO users (name, email, password_hash, must_change_password, account_role) "
                    + "VALUES (?, ?, ?, true, ?) RETURNING id", Long.class, cleanName, cleanEmail, encoder.encode(password), accountRole);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("EMAIL_IN_USE"); // D1: also a deactivated account's email
        }
        audit.record(admin.id(), id, "ACCOUNT_CREATED", null, null, null, Map.of("accountRole", accountRole));
        return find(id);
    }

    /**
     * Deactivation (FR1b, D3 to D5): the account can no longer log in, its open sessions stop at the next
     * request, and it leaves every project (assignments removed, tasks and comments kept).
     */
    @Transactional
    public AdminAccount deactivate(AuthenticatedUser admin, long userId) {
        requireAdmin(admin);
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.conflict("NOT_ACTIVE"); // D3
        }
        if (user.getId() == admin.id() || (user.getAccountRole() == User.AccountRole.ADMIN && activeAdmins() <= 1)) {
            throw ApiException.conflict("LAST_ADMIN"); // D4
        }
        members.lockProjectsOf(userId); // before the check, so a parallel demotion cannot slip in (D5)
        if (!members.projectsWhereOnlyManager(userId).isEmpty()) {
            throw ApiException.conflict("ONLY_MANAGER"); // D5: appoint another Manager first
        }
        members.removeAllMemberships(userId, admin.id());
        OffsetDateTime now = OffsetDateTime.now(clock);
        // pending reset links stop working, so a later reactivation starts from the Admin's password
        jdbc.update("UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL", now, userId);
        user.deactivate(now);
        audit.record(admin.id(), userId, "ACCOUNT_DEACTIVATED", null, null, Map.of("status", "ACTIVE"), Map.of("status", "DEACTIVATED"));
        users.flush();
        return find(userId);
    }

    /** Returning person (FR1b): same account and email, new initial password, no projects until added again (D19). */
    @Transactional
    public AdminAccount reactivate(AuthenticatedUser admin, long userId, String password) {
        requireAdmin(admin);
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (user.getStatus() != User.Status.DEACTIVATED) {
            throw ApiException.conflict(user.getStatus() == User.Status.ANONYMISED ? "ANONYMISED" : "ALREADY_ACTIVE"); // D3, D44
        }
        checkPassword(password, "initialPassword");
        user.reactivate(encoder.encode(password));
        audit.record(admin.id(), userId, "ACCOUNT_REACTIVATED", null, null, Map.of("status", "DEACTIVATED"), Map.of("status", "ACTIVE"));
        audit.record(admin.id(), userId, "PASSWORD_SET_BY_ADMIN");
        users.flush();
        return find(userId);
    }

    /** GDPR erasure of a deactivated account (docs/database.md section 5); cannot be undone (D44). */
    @Transactional
    public AdminAccount anonymise(AuthenticatedUser admin, long userId) {
        requireAdmin(admin);
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (user.getStatus() != User.Status.DEACTIVATED) {
            throw ApiException.conflict(user.getStatus() == User.Status.ANONYMISED ? "ANONYMISED" : "NOT_DEACTIVATED");
        }
        user.anonymise();
        audit.record(admin.id(), userId, "ACCOUNT_ANONYMISED", null, null, Map.of("status", "DEACTIVATED"), Map.of("status", "ANONYMISED"));
        users.flush();
        return find(userId);
    }

    static void requireAdmin(AuthenticatedUser user) {
        if (!"ADMIN".equals(user.accountRole())) {
            throw ApiException.forbidden(); // D2
        }
    }

    private AdminAccount find(long id) {
        return jdbc.queryForObject("SELECT id, name, email, account_role, status, must_change_password, created_at, "
                        + "deactivated_at FROM users WHERE id = ?",
                (rs, i) -> new AdminAccount(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("account_role"), rs.getString("status"), rs.getBoolean("must_change_password"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("deactivated_at", OffsetDateTime.class)),
                id);
    }

    private int activeAdmins() {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE account_role = 'ADMIN' AND status = 'ACTIVE'", Integer.class);
    }

    private static void checkPassword(String password, String field) {
        if (password == null || password.isEmpty()) {
            throw ApiException.invalid(field, "REQUIRED");
        }
        if (password.length() < AccountService.MIN_PASSWORD_LENGTH) {
            throw ApiException.invalid(field, "TOO_SHORT"); // A21
        }
        if (password.length() > AccountService.MAX_PASSWORD_LENGTH) {
            throw ApiException.invalid(field, "TOO_LONG");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

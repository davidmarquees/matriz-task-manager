package pt.matriz.accounts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.audit.UserAuditLog;
import pt.matriz.email.EmailSender;
import pt.matriz.web.ApiException;

/**
 * Password reset by email (FR1c, US11, D30 to D36). The answer to a request never reveals whether the
 * email exists; links are single-use, expire after 30 minutes and only their SHA-256 hash is stored
 * (NFR3). The token never appears in logs.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final UserAuditLog audit;
    private final EmailSender email;
    private final Clock clock;
    private final String appUrl;
    private final Duration expiry;
    private final int maxPerHour;

    public PasswordResetService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder, UserAuditLog audit,
                                EmailSender email, Clock clock,
                                @Value("${matriz.app-url}") String appUrl,
                                @Value("${matriz.password-reset.expiry}") Duration expiry,
                                @Value("${matriz.password-reset.max-per-hour}") int maxPerHour) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.email = email;
        this.clock = clock;
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.expiry = expiry;
        this.maxPerHour = maxPerHour;
    }

    /**
     * Sends a reset link when the email belongs to an active account and the hourly limit is not reached
     * (D30, D31). Older links are invalidated (D33). A mail server failure is logged without the token and
     * the answer stays the same (D36).
     */
    @Transactional
    public void request(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            throw ApiException.invalid("email", "REQUIRED");
        }
        if (emailAddress.length() > 254) {
            throw ApiException.invalid("email", "TOO_LONG"); // openapi.yaml maxLength; says nothing about accounts
        }
        Optional<User> found = users.findByEmailIgnoreCase(emailAddress.trim()).filter(User::isActive);
        if (found.isEmpty()) {
            return; // D30: same answer, no email
        }
        User user = found.get();
        OffsetDateTime now = OffsetDateTime.now(clock);
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id = ? AND created_at > ?",
                Integer.class, user.getId(), now.minusHours(1));
        if (recent != null && recent >= maxPerHour) {
            return; // D31: same answer, no more emails this hour
        }
        jdbc.update("UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL", now, user.getId());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)",
                user.getId(), sha256(token), now.plus(expiry), now);
        audit.record(null, user.getId(), "PASSWORD_RESET_REQUESTED");

        String link = appUrl + "/reset-password?token=" + token;
        try {
            if ("en".equals(user.getLocale())) {
                email.send(user.getEmail(), "Matriz · Reset your password",
                        "Hello " + user.getName() + ",\n\nTo choose a new password, open this link (valid for "
                                + expiry.toMinutes() + " minutes, single use):\n" + link
                                + "\n\nIf you did not ask for it, ignore this email: your password does not change.\n");
            } else {
                email.send(user.getEmail(), "Matriz · Redefinir a palavra-passe",
                        "Olá " + user.getName() + ",\n\nPara escolher uma nova palavra-passe, abra este link (válido durante "
                                + expiry.toMinutes() + " minutos, uma só vez):\n" + link
                                + "\n\nSe não fez este pedido, ignore este email: a palavra-passe não muda.\n");
            }
        } catch (RuntimeException e) {
            log.error("Password reset email could not be sent (user id {}): {}", user.getId(), e.getClass().getSimpleName());
        }
    }

    /**
     * Sets the new password with a valid link (D34): the token is used up, every other link invalidated,
     * the lock and failed attempts cleared. Returns the user id so the caller can end the user's sessions.
     * Invalid, expired or used link → 422 INVALID_TOKEN (D32); short password → 422 TOO_SHORT (D35).
     */
    @Transactional
    public long confirm(String token, String newPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw ApiException.invalid("newPassword", "REQUIRED");
        }
        if (newPassword.length() < AccountService.MIN_PASSWORD_LENGTH) {
            throw ApiException.invalid("newPassword", "TOO_SHORT");
        }
        if (newPassword.length() > AccountService.MAX_PASSWORD_LENGTH) {
            throw ApiException.invalid("newPassword", "TOO_LONG");
        }
        if (token == null || token.isBlank() || token.length() > 200) {
            throw ApiException.invalid("token", "INVALID_TOKEN");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Long> userIds = jdbc.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash = ? "
                + "AND used_at IS NULL AND expires_at > ? FOR UPDATE", Long.class, sha256(token), now);
        if (userIds.isEmpty()) {
            throw ApiException.invalid("token", "INVALID_TOKEN");
        }
        User user = users.findById(userIds.get(0)).filter(User::isActive)
                .orElseThrow(() -> ApiException.invalid("token", "INVALID_TOKEN"));
        user.resetPassword(encoder.encode(newPassword));
        jdbc.update("UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL", now, user.getId());
        audit.record(null, user.getId(), "PASSWORD_RESET_COMPLETED");
        users.flush();
        return user.getId();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

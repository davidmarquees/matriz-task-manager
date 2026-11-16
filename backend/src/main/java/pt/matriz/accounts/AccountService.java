package pt.matriz.accounts;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.matriz.audit.UserAuditLog;
import pt.matriz.web.ApiException;

/**
 * Accounts module service: credential checks, login lockout (A17) and own password change (FR1e).
 * Other modules use this service, never the repository.
 */
@Service
public class AccountService {

    static final int MIN_PASSWORD_LENGTH = 8; // A21
    static final int MAX_PASSWORD_LENGTH = 72; // BCrypt limit

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final UserAuditLog audit;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    /** Hash compared when the email is unknown, so the answer takes the same time (D6). */
    private final String dummyHash;

    public AccountService(UserRepository users, PasswordEncoder encoder, UserAuditLog audit, Clock clock,
                          @Value("${matriz.login.max-failed-attempts}") int maxFailedAttempts,
                          @Value("${matriz.login.lock-duration}") Duration lockDuration) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = lockDuration;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /**
     * Checks email and password. Empty for every failure: unknown email, wrong password, account not
     * active or locked (D6, D7), so the caller gives the same answer in every case.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Optional<AccountView> authenticate(String email, String password) {
        Optional<User> found = users.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty() || found.get().getPasswordHash() == null) {
            encoder.matches(password, dummyHash);
            return Optional.empty();
        }
        User user = found.get();
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean passwordOk = encoder.matches(password, user.getPasswordHash());
        if (!user.isActive() || user.isLockedAt(now)) {
            return Optional.empty(); // locked: refused even with the correct password (D7)
        }
        if (!passwordOk) {
            registerFailure(user, now);
            return Optional.empty();
        }
        user.registerSuccessfulLogin();
        return Optional.of(AccountView.of(user));
    }

    /** Account with this email (ignoring case), if any. */
    @Transactional(readOnly = true)
    public Optional<AccountView> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email.trim()).map(AccountView::of);
    }

    /** Public summaries (id, name, active) of these accounts, by id. */
    @Transactional(readOnly = true)
    public Map<Long, UserSummary> summaries(Collection<Long> userIds) {
        return users.findAllById(userIds).stream()
                .map(UserSummary::of)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    /** Current state of an account, read on every request (D8, D45). */
    @Transactional(readOnly = true)
    public Optional<AccountView> find(long userId) {
        return users.findById(userId).map(AccountView::of);
    }

    /**
     * Own password change (D46 to D48). Refuses a wrong current password (counted as a failed login
     * attempt), a new password that is too short or too long, or equal to the current one.
     * Ending the other sessions is done by the caller after this transaction.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void changeOwnPassword(long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            registerFailure(user, OffsetDateTime.now(clock));
            throw ApiException.invalid("currentPassword", "WRONG_PASSWORD");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.invalid("newPassword", "TOO_SHORT");
        }
        if (newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw ApiException.invalid("newPassword", "TOO_LONG");
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.invalid("newPassword", "SAME_AS_CURRENT");
        }
        user.changePassword(encoder.encode(newPassword));
        audit.record(userId, userId, "PASSWORD_CHANGED");
    }

    /**
     * Saves the interface language and theme (NFR1, NFR8). Unknown values → 422 INVALID_VALUE; nothing
     * sent → 422 REQUIRED. Written to the user audit log when something changed (PREFERENCES_CHANGED).
     */
    @Transactional
    public AccountView updatePreferences(long userId, String locale, String theme) {
        if (locale == null && theme == null) {
            throw ApiException.invalid("body", "REQUIRED");
        }
        if (locale != null && !java.util.Set.of("pt-PT", "en").contains(locale)) {
            throw ApiException.invalid("locale", "INVALID_VALUE");
        }
        if (theme != null && !java.util.Set.of("LIGHT", "DARK", "SYSTEM").contains(theme)) {
            throw ApiException.invalid("theme", "INVALID_VALUE");
        }
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        String newLocale = locale == null ? user.getLocale() : locale;
        String newTheme = theme == null ? user.getTheme() : theme;
        if (!newLocale.equals(user.getLocale()) || !newTheme.equals(user.getTheme())) {
            java.util.Map<String, Object> before = java.util.Map.of("locale", user.getLocale(), "theme", user.getTheme());
            java.util.Map<String, Object> after = java.util.Map.of("locale", newLocale, "theme", newTheme);
            user.changePreferences(newLocale, newTheme);
            audit.record(userId, userId, "PREFERENCES_CHANGED", null, null, before, after);
        }
        return AccountView.of(user);
    }

    private void registerFailure(User user, OffsetDateTime now) {
        if (user.registerFailedAttempt(maxFailedAttempts, now.plus(lockDuration))) {
            audit.record(null, user.getId(), "ACCOUNT_LOCKED");
        }
    }
}

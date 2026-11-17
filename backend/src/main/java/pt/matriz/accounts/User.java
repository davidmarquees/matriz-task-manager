package pt.matriz.accounts;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** User account (docs/database.md, table `users`). Accounts are never deleted. */
@Entity
@Table(name = "users")
public class User {

    public enum AccountRole { ADMIN, MANAGER, MEMBER }

    public enum Status { ACTIVE, DEACTIVATED, ANONYMISED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "must_change_password")
    private boolean mustChangePassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role")
    private AccountRole accountRole;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String locale;

    private String theme;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    protected User() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    public Status getStatus() {
        return status;
    }

    public String getLocale() {
        return locale;
    }

    public String getTheme() {
        return theme;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isLockedAt(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed attempt; returns true when this attempt locks the account (A17). */
    boolean registerFailedAttempt(int maxAttempts, OffsetDateTime lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            failedLoginAttempts = 0;
            lockedUntil = lockUntil;
            return true;
        }
        return false;
    }

    void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    void changePreferences(String newLocale, String newTheme) {
        locale = newLocale;
        theme = newTheme;
    }

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    /** FR1b: deactivated accounts cannot log in; their name stays on past work. */
    void deactivate(OffsetDateTime now) {
        status = Status.DEACTIVATED;
        deactivatedAt = now;
    }

    /** Returning person: same account, new initial password that must be changed (FR1b, FR1e). */
    void reactivate(String newHash) {
        status = Status.ACTIVE;
        deactivatedAt = null;
        passwordHash = newHash;
        mustChangePassword = true;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /** GDPR erasure (docs/database.md section 5): personal data removed, cannot be undone. */
    void anonymise() {
        status = Status.ANONYMISED;
        name = null;
        email = "removed-" + id + "@invalid";
        passwordHash = null;
    }

    /** Successful password reset (D34): new password, lock and failed attempts cleared. */
    void resetPassword(String newHash) {
        passwordHash = newHash;
        mustChangePassword = false;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public OffsetDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    void changePassword(String newHash) {
        passwordHash = newHash;
        mustChangePassword = false;
    }
}

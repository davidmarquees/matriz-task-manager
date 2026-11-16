package pt.matriz.accounts;

/** Read-only view of an account for other modules (no password hash). */
public record AccountView(long id, String name, String email, User.AccountRole accountRole,
                          boolean mustChangePassword, String locale, String theme, boolean active) {

    static AccountView of(User user) {
        return new AccountView(user.getId(), user.getName(), user.getEmail(), user.getAccountRole(),
                user.isMustChangePassword(), user.getLocale(), user.getTheme(), user.isActive());
    }
}

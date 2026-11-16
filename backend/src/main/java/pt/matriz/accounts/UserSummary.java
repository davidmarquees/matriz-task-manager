package pt.matriz.accounts;

/**
 * UserSummary of openapi.yaml: what other users may see of an account. `name` is null for an
 * anonymised account ("Removed user"); `active` is false for a deactivated one ("left the company").
 */
public record UserSummary(long id, String name, boolean active) {

    static UserSummary of(User user) {
        return new UserSummary(user.getId(), user.getName(), user.isActive());
    }
}

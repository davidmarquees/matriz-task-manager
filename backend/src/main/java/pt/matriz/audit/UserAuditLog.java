package pt.matriz.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only user audit log (FR1d). Must be called inside the transaction of the change,
 * so a failure rolls the change back (D37). Never writes passwords, hashes, tokens or personal values.
 */
@Service
public class UserAuditLog {

    private final JdbcTemplate jdbc;

    public UserAuditLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param actorId who made the change; null for system actions (account lock, seed data)
     * @param targetUserId whose account changed
     * @param action one of the actions of docs/database.md, e.g. PASSWORD_CHANGED
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, long targetUserId, String action) {
        jdbc.update("INSERT INTO user_audit_log (actor_id, target_user_id, action) VALUES (?, ?, ?)",
                actorId, targetUserId, action);
    }
}

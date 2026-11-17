package pt.matriz.audit;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes the append-only user audit log (FR1d). Must be called inside the transaction of the change,
 * so a failure rolls the change back (D37). Never writes passwords, hashes, tokens or personal values.
 */
@Service
public class UserAuditLog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public UserAuditLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param actorId who made the change; null for system actions (account lock, seed data)
     * @param targetUserId whose account changed
     * @param action one of the actions of docs/database.md, e.g. PASSWORD_CHANGED
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, long targetUserId, String action) {
        record(actorId, targetUserId, action, null, null, null, null);
    }

    /** Membership changes: the project concerned and the non-personal values before and after. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, long targetUserId, String action, Long projectId, String projectName,
                       Map<String, ?> oldValue, Map<String, ?> newValue) {
        jdbc.update("INSERT INTO user_audit_log (actor_id, target_user_id, action, project_id, project_name, "
                        + "old_value, new_value) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)",
                actorId, targetUserId, action, projectId, projectName, toJson(oldValue), toJson(newValue));
    }

    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit value cannot be written as JSON", e);
        }
    }
}

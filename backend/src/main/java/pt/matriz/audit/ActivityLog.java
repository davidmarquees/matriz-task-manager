package pt.matriz.audit;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes the append-only project activity log (FR13), in the transaction of the change, so the change
 * is rolled back if the entry cannot be written (D40). No personal values (names, emails) are written:
 * people are identified by id only (docs/database.md, activity_log).
 */
@Service
public class ActivityLog {

    public enum EntityType { PROJECT, MEMBER, TASK, ASSIGNEE, COMMENT }

    public enum Action { CREATED, UPDATED, DELETED, ADDED, REMOVED }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ActivityLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param changes changed fields with old and new values, e.g. {"status": {"old": "TODO", "new": "DONE"}};
     *                null when there is nothing to add
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long projectId, String projectName, long actorId, EntityType entityType, long entityId,
                       Action action, Map<String, ?> changes) {
        jdbc.update("INSERT INTO activity_log (project_id, project_name, actor_id, entity_type, entity_id, action, changes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
                projectId, projectName, actorId, entityType.name(), entityId, action.name(), toJson(changes));
    }

    /** {"field": {"old": ..., "new": ...}} for one field. */
    public static Map<String, Object> change(Object oldValue, Object newValue) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("old", oldValue);
        values.put("new", newValue);
        return values;
    }

    private String toJson(Map<String, ?> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Activity changes cannot be written as JSON", e);
        }
    }
}

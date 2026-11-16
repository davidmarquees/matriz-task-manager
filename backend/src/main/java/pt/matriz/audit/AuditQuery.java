package pt.matriz.audit;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.UserSummary;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.web.ApiException;

/** Reading the user audit log (FR1d, US12): Admin only (D38); nobody can change it (D39). */
@Service
public class AuditQuery {

    public record Entry(long id, @JsonInclude(JsonInclude.Include.ALWAYS) UserSummary actor, UserSummary targetUser,
                        String action, Long projectId, String projectName, JsonNode oldValue, JsonNode newValue,
                        OffsetDateTime createdAt) {
    }

    public record EntryPage(List<Entry> items, int page, int size, long totalItems) {
    }

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final ObjectMapper json;

    public AuditQuery(JdbcTemplate jdbc, AccountService accounts, ObjectMapper json) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.json = json;
    }

    /** Newest first; optionally only the entries about one account. */
    @Transactional(readOnly = true)
    public EntryPage list(AuthenticatedUser user, Long targetUserId, int page, int size) {
        if (!"ADMIN".equals(user.accountRole())) {
            throw ApiException.forbidden();
        }
        if (page < 0) {
            throw ApiException.invalid("page", "INVALID_VALUE");
        }
        if (size < 1 || size > 100) {
            throw ApiException.invalid("size", "INVALID_VALUE");
        }
        String where = targetUserId == null ? "" : " WHERE target_user_id = ?";
        List<Object> params = new ArrayList<>();
        if (targetUserId != null) params.add(targetUserId);
        long total = jdbc.queryForObject("SELECT count(*) FROM user_audit_log" + where, Long.class, params.toArray());
        params.add(size);
        params.add((long) page * size);
        record Row(long id, Long actorId, long targetId, String action, Long projectId, String projectName,
                   String oldValue, String newValue, OffsetDateTime createdAt) {
        }
        List<Row> rows = jdbc.query("SELECT id, actor_id, target_user_id, action, project_id, project_name, "
                        + "old_value::text AS old_value, new_value::text AS new_value, created_at FROM user_audit_log"
                        + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new Row(rs.getLong("id"), (Long) rs.getObject("actor_id", Long.class), rs.getLong("target_user_id"),
                        rs.getString("action"), (Long) rs.getObject("project_id", Long.class), rs.getString("project_name"),
                        rs.getString("old_value"), rs.getString("new_value"), rs.getObject("created_at", OffsetDateTime.class)),
                params.toArray());
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> {
            ids.add(r.targetId());
            if (r.actorId() != null) ids.add(r.actorId());
        });
        Map<Long, UserSummary> users = accounts.summaries(ids);
        List<Entry> items = rows.stream().map(r -> new Entry(r.id(), r.actorId() == null ? null : users.get(r.actorId()),
                users.get(r.targetId()), r.action(), r.projectId(), r.projectName(), parse(r.oldValue()), parse(r.newValue()),
                r.createdAt())).toList();
        return new EntryPage(items, page, size, total);
    }

    private JsonNode parse(String text) {
        if (text == null) return null;
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable audit entry", e);
        }
    }
}

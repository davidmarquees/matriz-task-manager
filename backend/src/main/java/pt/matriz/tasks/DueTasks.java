package pt.matriz.tasks;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasks with a due date in a date range, over a set of projects (dashboard weekly widget, UI polish
 * approved 2026-11-18). Owned by the tasks module and used by the dashboard module. Read-only.
 */
@Service
public class DueTasks {

    /** DueTask schema of openapi.yaml. */
    public record DueTask(long id, long projectId, String projectName, String title, String status,
                          String priority, LocalDate dueDate) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DueTasks(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sorted by due date, then priority (HIGH first), then title; both ends of the range are included. */
    @Transactional(readOnly = true)
    public List<DueTask> between(Collection<Long> projectIds, LocalDate from, LocalDate to) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT t.id, t.project_id, p.name AS project_name, t.title, t.status, t.priority, t.due_date "
                        + "FROM tasks t JOIN projects p ON p.id = t.project_id "
                        + "WHERE t.project_id IN (:ids) AND t.due_date BETWEEN :from AND :to "
                        + "ORDER BY t.due_date, CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, "
                        + "lower(t.title), t.id",
                Map.of("ids", projectIds, "from", from, "to", to),
                (rs, i) -> new DueTask(rs.getLong("id"), rs.getLong("project_id"), rs.getString("project_name"),
                        rs.getString("title"), rs.getString("status"), rs.getString("priority"),
                        rs.getObject("due_date", LocalDate.class)));
    }
}

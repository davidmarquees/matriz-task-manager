package pt.matriz.tasks;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Task counts per status (FR9, A9), owned by the tasks module and used by the projects and dashboard
 * modules. Always computed over all the project's tasks, never over a page.
 */
@Service
public class TaskCounts {

    /** TaskCounts schema of openapi.yaml. */
    public record Counts(@JsonProperty("TODO") long todo,
                         @JsonProperty("IN_PROGRESS") long inProgress,
                         @JsonProperty("DONE") long done) {
        public static final Counts ZERO = new Counts(0, 0, 0); // E2
    }

    /** PriorityCounts schema of openapi.yaml: open tasks (TODO or IN_PROGRESS) per priority. */
    public record PriorityCounts(@JsonProperty("LOW") long low,
                                 @JsonProperty("MEDIUM") long medium,
                                 @JsonProperty("HIGH") long high) {
        public static final PriorityCounts ZERO = new PriorityCounts(0, 0, 0);
    }

    private final NamedParameterJdbcTemplate jdbc;

    public TaskCounts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Counts for each project id; projects without tasks get 0, 0, 0 (E2). */
    @Transactional(readOnly = true)
    public Map<Long, Counts> byProject(Collection<Long> projectIds) {
        Map<Long, Counts> result = new HashMap<>();
        projectIds.forEach(id -> result.put(id, Counts.ZERO));
        if (projectIds.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT project_id, "
                        + "count(*) FILTER (WHERE status = 'TODO') AS todo, "
                        + "count(*) FILTER (WHERE status = 'IN_PROGRESS') AS in_progress, "
                        + "count(*) FILTER (WHERE status = 'DONE') AS done "
                        + "FROM tasks WHERE project_id IN (:ids) GROUP BY project_id",
                Map.of("ids", projectIds));
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("project_id")).longValue(), new Counts(
                    ((Number) row.get("todo")).longValue(),
                    ((Number) row.get("in_progress")).longValue(),
                    ((Number) row.get("done")).longValue()));
        }
        return result;
    }

    /** Open tasks (not DONE) per priority, summed over the given projects (dashboard chart). */
    @Transactional(readOnly = true)
    public PriorityCounts openByPriority(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return PriorityCounts.ZERO;
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT count(*) FILTER (WHERE priority = 'LOW') AS low, "
                        + "count(*) FILTER (WHERE priority = 'MEDIUM') AS medium, "
                        + "count(*) FILTER (WHERE priority = 'HIGH') AS high "
                        + "FROM tasks WHERE project_id IN (:ids) AND status <> 'DONE'",
                Map.of("ids", projectIds));
        return new PriorityCounts(((Number) row.get("low")).longValue(), ((Number) row.get("medium")).longValue(),
                ((Number) row.get("high")).longValue());
    }
}

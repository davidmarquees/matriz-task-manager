package pt.matriz.dashboard;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pt.matriz.projects.ProjectAccess;
import pt.matriz.projects.ProjectService;
import pt.matriz.projects.ProjectViews.ProjectSummary;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.tasks.DueTasks;
import pt.matriz.tasks.TaskCounts;
import pt.matriz.web.ApiException;

/**
 * getDashboard (FR9, E2): every project of the user with task counts per status, plus totals across
 * them, including open tasks per priority. listDashboardDueTasks: tasks due in a date range, for the
 * weekly widget (UI polish, 2026-11-18). Read-only. The Admin account has no dashboard (403).
 */
@RestController
class DashboardController {

    /** Longest range accepted by /dashboard/due-tasks, both ends included (openapi.yaml). */
    static final long MAX_RANGE_DAYS = 42;

    record Totals(long projects, TaskCounts.Counts taskCounts, TaskCounts.PriorityCounts priorityCounts) {
    }

    record Dashboard(Totals totals, List<ProjectSummary> projects) {
    }

    record DueTaskList(List<DueTasks.DueTask> items) {
    }

    private final ProjectService projects;
    private final TaskCounts taskCounts;
    private final DueTasks dueTasks;

    DashboardController(ProjectService projects, TaskCounts taskCounts, DueTasks dueTasks) {
        this.projects = projects;
        this.taskCounts = taskCounts;
        this.dueTasks = dueTasks;
    }

    @GetMapping("/dashboard")
    Dashboard get(@AuthenticationPrincipal AuthenticatedUser user) {
        if (ProjectAccess.isAdmin(user)) {
            throw ApiException.forbidden();
        }
        List<ProjectSummary> mine = projects.allOfMember(user);
        long todo = 0;
        long inProgress = 0;
        long done = 0;
        for (ProjectSummary p : mine) {
            todo += p.taskCounts().todo();
            inProgress += p.taskCounts().inProgress();
            done += p.taskCounts().done();
        }
        TaskCounts.PriorityCounts byPriority = taskCounts.openByPriority(ids(mine));
        return new Dashboard(new Totals(mine.size(), new TaskCounts.Counts(todo, inProgress, done), byPriority), mine);
    }

    @GetMapping("/dashboard/due-tasks")
    DueTaskList dueTasks(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to) {
        if (ProjectAccess.isAdmin(user)) {
            throw ApiException.forbidden();
        }
        LocalDate start = date("from", from);
        LocalDate end = date("to", to);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.invalid("to", "INVALID_VALUE");
        }
        return new DueTaskList(dueTasks.between(ids(projects.allOfMember(user)), start, end));
    }

    private static List<Long> ids(List<ProjectSummary> mine) {
        return mine.stream().map(ProjectSummary::id).toList();
    }

    private static LocalDate date(String field, String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.invalid(field, "REQUIRED");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid(field, "INVALID_DATE");
        }
    }
}

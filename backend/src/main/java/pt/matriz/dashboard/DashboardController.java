package pt.matriz.dashboard;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.matriz.projects.ProjectAccess;
import pt.matriz.projects.ProjectService;
import pt.matriz.projects.ProjectViews.ProjectSummary;
import pt.matriz.security.AuthenticatedUser;
import pt.matriz.tasks.TaskCounts;
import pt.matriz.web.ApiException;

/**
 * getDashboard (FR9, E2): every project of the user with task counts per status, plus totals across
 * them. Read-only. The Admin account has no dashboard (403).
 */
@RestController
class DashboardController {

    record Totals(long projects, TaskCounts.Counts taskCounts) {
    }

    record Dashboard(Totals totals, List<ProjectSummary> projects) {
    }

    private final ProjectService projects;

    DashboardController(ProjectService projects) {
        this.projects = projects;
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
        return new Dashboard(new Totals(mine.size(), new TaskCounts.Counts(todo, inProgress, done)), mine);
    }
}

package pt.matriz.audit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pt.matriz.security.AuthenticatedUser;

/** listProjectActivity of openapi.yaml. */
@RestController
class ActivityController {

    private final ActivityQuery activity;

    ActivityController(ActivityQuery activity) {
        this.activity = activity;
    }

    @GetMapping("/projects/{projectId}/activity")
    ActivityQuery.EntryPage list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long projectId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return activity.list(user, projectId, page, size);
    }
}

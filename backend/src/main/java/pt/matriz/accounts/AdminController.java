package pt.matriz.accounts;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pt.matriz.accounts.AdminService.AdminAccount;
import pt.matriz.audit.AuditQuery;
import pt.matriz.security.AuthenticatedUser;

/** Account administration and the user audit log (openapi.yaml, tag Admin). Admin account only. */
@RestController
@RequestMapping("/admin")
class AdminController {

    record AccountCreateRequest(String name, String email, String accountRole, String initialPassword) {
    }

    record PasswordRequest(String initialPassword) {
    }

    private final AdminService admin;
    private final AuditQuery audit;

    AdminController(AdminService admin, AuditQuery audit) {
        this.admin = admin;
        this.audit = audit;
    }

    @GetMapping("/users")
    List<AdminAccount> list(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String q) {
        return admin.list(user, status, q);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    AdminAccount create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody AccountCreateRequest body) {
        return admin.create(user, body.name(), body.email(), body.accountRole(), body.initialPassword());
    }

    @PostMapping("/users/{userId}/deactivate")
    AdminAccount deactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long userId) {
        return admin.deactivate(user, userId);
    }

    @PostMapping("/users/{userId}/reactivate")
    AdminAccount reactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long userId,
                            @RequestBody PasswordRequest body) {
        return admin.reactivate(user, userId, body.initialPassword());
    }

    @PostMapping("/users/{userId}/anonymise")
    AdminAccount anonymise(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long userId) {
        return admin.anonymise(user, userId);
    }

    @GetMapping("/audit")
    AuditQuery.EntryPage auditLog(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) Long userId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return audit.list(user, userId, page, size);
    }
}

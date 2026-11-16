package pt.matriz.security;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.AccountView;
import pt.matriz.web.ApiException;

/** Auth operations of openapi.yaml: login, logout, getMe, changeOwnPassword. */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final AccountService accounts;
    private final LoginRateLimiter rateLimiter;
    private final SessionAuthenticationStrategy loginSessionStrategy;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrfTokens;
    private final SessionRegistry sessions;

    AuthController(AccountService accounts, LoginRateLimiter rateLimiter,
                   SessionAuthenticationStrategy loginSessionStrategy, SecurityContextRepository contexts,
                   CsrfTokenRepository csrfTokens, SessionRegistry sessions) {
        this.accounts = accounts;
        this.rateLimiter = rateLimiter;
        this.loginSessionStrategy = loginSessionStrategy;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
        this.sessions = sessions;
    }

    record LoginRequest(@NotNull @Size(max = 254) String email, @NotNull @Size(max = 100) String password) {
    }

    record PasswordChangeRequest(@NotNull @Size(max = 100) String currentPassword, @NotNull String newPassword) {
    }

    record CurrentUserResponse(long id, String name, String email, String accountRole, boolean mustChangePassword,
                               String locale, String theme) {
        static CurrentUserResponse of(AccountView account) {
            return new CurrentUserResponse(account.id(), account.name(), account.email(),
                    account.accountRole().name(), account.mustChangePassword(), account.locale(), account.theme());
        }
    }

    @PostMapping("/login")
    CurrentUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                              HttpServletResponse response) {
        rateLimiter.register(request.getRemoteAddr());
        AccountView account = accounts.authenticate(body.email(), body.password())
                .orElseThrow(ApiException::loginFailed);

        AuthenticatedUser principal = new AuthenticatedUser(account.id(), account.accountRole().name());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.accountRole())));
        loginSessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return CurrentUserResponse.of(account);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextLogoutHandler contextLogout = new SecurityContextLogoutHandler();
        contextLogout.setSecurityContextRepository(contexts);
        contextLogout.logout(request, response, authentication);
        new CsrfLogoutHandler(csrfTokens).logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        // A fresh CSRF token, so the next login from the same page works without reloading it.
        csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.find(user.id()).map(CurrentUserResponse::of).orElseThrow(ApiException::notFound);
    }

    @PostMapping("/password")
    ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody PasswordChangeRequest body,
                                           @AuthenticationPrincipal AuthenticatedUser user,
                                           HttpServletRequest request) {
        accounts.changeOwnPassword(user.id(), body.currentPassword(), body.newPassword());

        // D48: end every other session of the user, then give this session a new id.
        String current = request.getSession().getId();
        for (SessionInformation session : sessions.getAllSessions(user, false)) {
            if (!session.getSessionId().equals(current)) {
                session.expireNow();
            }
        }
        request.changeSessionId();
        return ResponseEntity.noContent().build();
    }
}

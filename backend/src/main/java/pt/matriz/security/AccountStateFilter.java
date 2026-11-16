package pt.matriz.security;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import pt.matriz.accounts.AccountService;
import pt.matriz.accounts.AccountView;
import pt.matriz.web.Problems;

/**
 * Checks the account on every authenticated request, from the database (not cached in the session):
 * <ul>
 *   <li>D8: an account that is no longer active ends the session and gets 401;</li>
 *   <li>D45: while a password change is required, only getMe, logout and changeOwnPassword are allowed (403).</li>
 * </ul>
 */
final class AccountStateFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED =
            Set.of("/auth/me", "/auth/logout", "/auth/password", "/auth/me/preferences");

    private final AccountService accounts;
    private final ObjectMapper json;

    AccountStateFilter(AccountService accounts, ObjectMapper json) {
        this.accounts = accounts;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            Optional<AccountView> account = accounts.find(user.id());
            if (account.isEmpty() || !account.get().active()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                Problems.write(response, json, Problems.of(HttpStatus.UNAUTHORIZED));
                return;
            }
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (account.get().mustChangePassword() && !ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED.contains(path)) {
                var problem = Problems.of(HttpStatus.FORBIDDEN);
                problem.setDetail("Password change required.");
                Problems.write(response, json, problem);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

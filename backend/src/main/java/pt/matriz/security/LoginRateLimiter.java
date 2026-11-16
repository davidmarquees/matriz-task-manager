package pt.matriz.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pt.matriz.web.ApiException;

/**
 * Limits login attempts per client IP address (approved in increment 2): at most
 * {@code max-attempts} in a sliding window, then 429 with Retry-After.
 *
 * <p>Kept in memory (one instance, NFR2 scale); a restart clears it. The IP is the address the server
 * sees; X-Forwarded-For is not trusted, because no trusted proxy is configured.
 */
@Component
public class LoginRateLimiter {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimiter(Clock clock,
                            @Value("${matriz.login.rate-limit.max-attempts}") int maxAttempts,
                            @Value("${matriz.login.rate-limit.window}") Duration window) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /** Records one attempt from this address, or throws 429 when the limit is reached. */
    public void register(String ip) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        Deque<Instant> times = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
                times.pollFirst();
            }
            if (times.size() >= maxAttempts) {
                long wait = Duration.between(windowStart, times.peekFirst()).toSeconds() + 1;
                throw ApiException.tooManyRequests(Math.max(1, wait));
            }
            times.addLast(now);
        }
        if (attempts.size() > 10_000) {
            attempts.values().removeIf(deque -> {
                synchronized (deque) {
                    return deque.isEmpty() || !deque.peekLast().isAfter(windowStart);
                }
            });
        }
    }
}

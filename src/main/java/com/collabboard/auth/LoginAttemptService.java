package com.collabboard.auth;

import com.collabboard.common.ApiException;
import com.collabboard.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for login. Failures are counted per email and per client IP;
 * after too many failures within the lock window further attempts are rejected until it expires.
 * (A single-instance monolith keeps this in memory; a clustered deployment would move it to a shared store.)
 */
@Component
public class LoginAttemptService {

    private record Entry(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptService(AppProperties props) {
        this.maxFailures = props.security().loginMaxFailures();
        this.lockDuration = Duration.ofMinutes(props.security().loginLockMinutes());
    }

    public void checkAllowed(String email, String ip) {
        Instant now = Instant.now();
        if (isLocked("email:" + email, now) || isLocked("ip:" + ip, now)) {
            throw ApiException.tooManyRequests("Too many failed sign-in attempts. Please try again in "
                    + lockDuration.toMinutes() + " minutes.");
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = Instant.now();
        bump("email:" + email, maxFailures, now);
        // An IP may legitimately serve many users (campus NAT), so it gets a higher threshold.
        bump("ip:" + ip, maxFailures * 4, now);
    }

    public void recordSuccess(String email) {
        entries.remove("email:" + email);
    }

    private boolean isLocked(String key, Instant now) {
        Entry e = entries.get(key);
        return e != null && e.lockedUntil() != null && e.lockedUntil().isAfter(now);
    }

    private void bump(String key, int threshold, Instant now) {
        if (entries.size() > MAX_TRACKED_KEYS) {
            // Bound memory under a flood of distinct keys: drop entries whose window and lock have expired.
            entries.entrySet().removeIf(en -> en.getValue().windowStart().plus(lockDuration).isBefore(now)
                    && (en.getValue().lockedUntil() == null || en.getValue().lockedUntil().isBefore(now)));
        }
        entries.compute(key, (k, e) -> {
            if (e == null || e.windowStart().plus(lockDuration).isBefore(now)
                    || (e.lockedUntil() != null && !e.lockedUntil().isAfter(now))) {
                e = new Entry(0, now, null);
            }
            int failures = e.failures() + 1;
            Instant lockedUntil = failures >= threshold ? now.plus(lockDuration) : null;
            return new Entry(failures, e.windowStart(), lockedUntil);
        });
    }

    /** Test hook. */
    public void reset() {
        entries.clear();
    }
}

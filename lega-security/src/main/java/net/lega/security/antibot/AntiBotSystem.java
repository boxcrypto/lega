package net.lega.security.antibot;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.lega.security.LegaSecurityManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public final class AntiBotSystem {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/AntiBot");

    // ── tuning constants ──────────────────────────────────────────────────────
    private static final int MAX_CONNECTIONS_PER_5S = 10;
    private static final int MAX_FAILED_LOGINS      = 5;

    // ── state ─────────────────────────────────────────────────────────────────
    private final LegaSecurityManager securityManager;
    private volatile boolean  lockdownMode  = false;
    private volatile long     lockdownUntil = 0L;
    private final AtomicLong  totalBotConnectionsBlocked = new AtomicLong(0);
    private Cache<String, AtomicInteger> connectionAttempts;
    private Cache<String, AtomicInteger> failedLogins;

    public AntiBotSystem(LegaSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void initialize() {
        this.connectionAttempts = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .maximumSize(50_000)
                .build();

        this.failedLogins = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();

        LOGGER.info("[LEGA/AntiBot] Anti-bot system ready. Max {}/5s per IP.", MAX_CONNECTIONS_PER_5S);
    }

    
    public boolean isFloodDetected(String ip) {
        if (lockdownMode && System.currentTimeMillis() < lockdownUntil) {
            totalBotConnectionsBlocked.incrementAndGet();
            return true;
        }

        AtomicInteger counter = connectionAttempts.get(ip, k -> new AtomicInteger(0));
        int attempts = counter.incrementAndGet();

        if (attempts > MAX_CONNECTIONS_PER_5S) {
            totalBotConnectionsBlocked.incrementAndGet();
            LOGGER.warn("[LEGA/AntiBot] Bot flood from IP: {} ({} connections in 5s)", ip, attempts);

            // If many IPs are flooding concurrently, enter lockdown
            if (attempts > MAX_CONNECTIONS_PER_5S * 5) {
                activateLockdown(30_000);
            }
            return true;
        }

        return false;
    }

    
    public void recordFailedLogin(String ip) {
        AtomicInteger counter = failedLogins.get(ip, k -> new AtomicInteger(0));
        int failures = counter.incrementAndGet();
        if (failures >= MAX_FAILED_LOGINS) {
            securityManager.blockIP(ip, "Excessive failed login attempts (" + failures + ")");
        }
    }

    
    public void activateLockdown(long durationMs) {
        if (!lockdownMode) {
            lockdownMode = true;
            lockdownUntil = System.currentTimeMillis() + durationMs;
            LOGGER.warn("[LEGA/AntiBot] ⚠ LOCKDOWN MODE ACTIVATED for {}s! Bot attack in progress.",
                    durationMs / 1000);
        }
    }

    
    public void deactivateLockdown() {
        if (lockdownMode) {
            lockdownMode = false;
            lockdownUntil = 0;
            LOGGER.info("[LEGA/AntiBot] Lockdown mode deactivated. Server resumed normal operation.");
        }
    }

    public boolean isLockdownActive() {
        if (lockdownMode && System.currentTimeMillis() >= lockdownUntil) {
            deactivateLockdown();
        }
        return lockdownMode;
    }

    public long getTotalBotConnectionsBlocked() { return totalBotConnectionsBlocked.get(); }
}

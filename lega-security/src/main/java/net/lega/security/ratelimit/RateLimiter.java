package net.lega.security.ratelimit;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public final class RateLimiter {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/RateLimit");

    private static final double MAX_TOKENS  = 500.0;  // max packets per window
    private static final double REFILL_RATE = 500.0;  // tokens refilled per second

    private Cache<String, TokenBucket> buckets;

    public void initialize() {
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
        LOGGER.info("[LEGA/RateLimit] Rate limiter ready. Max: {} pkt/s per IP", MAX_TOKENS);
    }

    
    public boolean checkAndConsume(String ip) {
        TokenBucket bucket = buckets.get(ip, k -> new TokenBucket(MAX_TOKENS, REFILL_RATE));
        return bucket.tryConsume();
    }

    
    public void reset(String ip) {
        buckets.invalidate(ip);
    }

    
    public double getTokens(String ip) {
        TokenBucket bucket = buckets.getIfPresent(ip);
        return bucket != null ? bucket.getTokens() : -1;
    }

    // =========================================================================
    // Token Bucket implementation
    // =========================================================================

    private static final class TokenBucket {
        private final double maxTokens;
        private final double refillPerNano;

        private double tokens;
        private long lastRefillNanos;

        TokenBucket(double maxTokens, double refillPerSecond) {
            this.maxTokens        = maxTokens;
            this.tokens           = maxTokens;
            this.refillPerNano    = refillPerSecond / 1_000_000_000.0;
            this.lastRefillNanos  = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            tokens = Math.min(maxTokens, tokens + elapsed * refillPerNano);
            lastRefillNanos = now;
        }

        synchronized double getTokens() {
            refill();
            return tokens;
        }
    }
}

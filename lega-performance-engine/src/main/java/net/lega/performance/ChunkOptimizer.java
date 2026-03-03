package net.lega.performance;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public final class ChunkOptimizer {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Chunk");

    public enum ThrottleLevel {
        /** Normal operation — full tick range. */
        NORMAL,
        /** Reduced tick range to reclaim CPU. */
        MODERATE,
        /** Minimal tick range for high-load situations. */
        AGGRESSIVE
    }

    // ── fields ───────────────────────────────────────────────────────────────
    private volatile boolean asyncChunkLoad   = true;
    private volatile boolean asyncLighting    = true;
    private volatile boolean chunkTickThrottle = false;
    private volatile boolean nioChunkIo       = true;

    private final java.util.concurrent.atomic.AtomicReference<ThrottleLevel> throttleLevel =
            new java.util.concurrent.atomic.AtomicReference<>(ThrottleLevel.NORMAL);

    private final AtomicLong chunksLoadedAsync     = new AtomicLong(0);
    private final AtomicLong chunksLoadedSync      = new AtomicLong(0);
    private final AtomicLong lightingJobsCompleted = new AtomicLong(0);

    public void initialize() {
        LOGGER.info("[LEGA/Chunk] Chunk optimizer initialized. asyncLoad={} asyncLighting={} throttle={}",
                asyncChunkLoad, asyncLighting, throttleLevel.get());
    }

    public boolean shouldTickChunk(int chunkX, int chunkZ, int nearestPlayerChunkDist, long currentTick) {
        if (!chunkTickThrottle) return true;

        return switch (throttleLevel.get()) {
            case NORMAL     -> nearestPlayerChunkDist <= 8;
            case MODERATE   -> nearestPlayerChunkDist <= 4;
            case AGGRESSIVE -> nearestPlayerChunkDist <= 2;
        };
    }

    
    public boolean isAsyncChunkLoadEnabled() {
        return asyncChunkLoad;
    }

    
    public boolean isAsyncLightingEnabled() {
        return asyncLighting;
    }

    // =========================================================================
    // State control
    // =========================================================================

    public void setThrottleLevel(ThrottleLevel level) {
        ThrottleLevel prev = throttleLevel.getAndSet(level);
        if (prev != level) {
            LOGGER.info("[LEGA/Chunk] Throttle level changed: {} → {}", prev, level);
        }
    }

    // =========================================================================
    // Stats
    // =========================================================================

    public long getChunksLoadedAsync() { return chunksLoadedAsync.get(); }
    public long getChunksLoadedSync()  { return chunksLoadedSync.get(); }
    public long getLightingJobsCompleted() { return lightingJobsCompleted.get(); }
    public ThrottleLevel getThrottleLevel() { return throttleLevel.get(); }

    // =========================================================================
    // Config setters
    // =========================================================================

    public void setAsyncChunkLoad(boolean val)  { this.asyncChunkLoad = val; }
    public void setAsyncLighting(boolean val)   { this.asyncLighting = val; }
    public void setChunkTickThrottle(boolean v) { this.chunkTickThrottle = v; }
    public void setNioChunkIo(boolean val)      { this.nioChunkIo = val; }
}

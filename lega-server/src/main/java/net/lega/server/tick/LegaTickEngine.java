package net.lega.server.tick;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.server.LegaServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;


public final class LegaTickEngine {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Tick");

    // ── tuning ────────────────────────────────────────────────────────────────
    /** Nanoseconds per tick at the target of 20 TPS (50 ms). */
    private static final long TARGET_TICK_NS = 50_000_000L;

    // ── dependencies ──────────────────────────────────────────────────────────
    private final LegaServer server;

    // ── state ─────────────────────────────────────────────────────────────────
    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final AtomicLong    tickCount  = new AtomicLong(0);

    // ── MSPT metrics ──────────────────────────────────────────────────────────
    private volatile double lastMspt = 0;
    private volatile double avgMspt  = 0;
    private volatile double maxMspt  = 0;

    // ── TPS ring buffers ──────────────────────────────────────────────────────
    private final long[] tps1Min  = new long[60];
    private final long[] tps5Min  = new long[300];
    private final long[] tps15Min = new long[900];
    private int  tpsIndex = 0;

    // ── overload / auto-perf ──────────────────────────────────────────────────
    private long    overloadWarningCooldownMs = 0L;
    private boolean autoPerformanceActive     = false;

    public LegaTickEngine(LegaServer server) {
        this.server = server;
    }

    
    public void run() {
        running.set(true);
        LOGGER.info("[LEGA/Tick] Tick engine started. Target: 20 TPS (50ms/tick).");

        long lastTickStart = System.nanoTime();
        long lastSecondTimestamp = System.nanoTime();
        int ticksThisSecond = 0;

        while (running.get()) {
            final long tickStart = System.nanoTime();

            try {
                runTick();
            } catch (Exception e) {
                LOGGER.error("[LEGA/Tick] Exception during tick #{}!", tickCount.get(), e);
            }

            final long tickEnd = System.nanoTime();
            final long tickDurationNs = tickEnd - tickStart;
            final double tickDurationMs = tickDurationNs / 1_000_000.0;

            updateMspt(tickDurationMs);
            tickCount.incrementAndGet();
            ticksThisSecond++;

            // Check for TPS every second
            long nowNs = System.nanoTime();
            if (nowNs - lastSecondTimestamp >= 1_000_000_000L) {
                recordTps(ticksThisSecond);
                ticksThisSecond = 0;
                lastSecondTimestamp = nowNs;
                checkOverload();
                checkAutoPerformance();
            }

            // Sleep for remaining time in this tick slot
            long elapsed = System.nanoTime() - tickStart;
            long sleepNs = TARGET_TICK_NS - elapsed;
            if (sleepNs > 0) {
                LockSupport.parkNanos(sleepNs);
            }
            // If sleepNs < 0, we're behind — log dynamic skip
            else if (sleepNs < -TARGET_TICK_NS * 2) {
                LOGGER.warn("[LEGA/Tick] Tick #{} overran by {}ms!", tickCount.get(),
                        String.format("%.2f", Math.abs(sleepNs / 1_000_000.0)));
            }
        }

        LOGGER.info("[LEGA/Tick] Tick engine stopped after {} ticks.", tickCount.get());
    }

    
    public void stop() {
        running.set(false);
        LOGGER.info("[LEGA/Tick] Stop requested.");
    }

    // =========================================================================
    // Tick dispatch
    // =========================================================================

    /**
     * Executes a single game tick.  Called once per 50 ms from {@link #run()}.
     * Plugin tasks, world updates and async callbacks are dispatched from here.
     */
    private void runTick() {
        // Tick dispatch is wired up by the server during bootstrap.
        // This hook is deliberately empty in the base engine implementation.
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    private void updateMspt(double mspt) {
        lastMspt = mspt;
        avgMspt  = avgMspt * 0.95 + mspt * 0.05; // EMA
        if (mspt > maxMspt) maxMspt = mspt;
    }

    private void recordTps(int ticksThisSecond) {
        // Clamp to 20 TPS max
        tps1Min[tpsIndex % tps1Min.length]   = Math.min(ticksThisSecond, 20);
        tps5Min[tpsIndex % tps5Min.length]   = Math.min(ticksThisSecond, 20);
        tps15Min[tpsIndex % tps15Min.length] = Math.min(ticksThisSecond, 20);
        tpsIndex++;
    }

    private void checkOverload() {
        double currentTps = getTPS()[0];
        if (currentTps < 18.0 && System.currentTimeMillis() > overloadWarningCooldownMs) {
            LOGGER.warn("[LEGA/Tick] Server is running behind! TPS: {}", String.format("%.2f", currentTps));
            overloadWarningCooldownMs = System.currentTimeMillis() + 10_000; // warn every 10s
        }
    }

    private void checkAutoPerformance() {
        if (!server.getConfiguration().isAutoPerformanceModeEnabled()) return;
        double tps = getTPS()[0];
        if (!autoPerformanceActive && tps < 17.0) {
            autoPerformanceActive = true;
            LOGGER.warn("[LEGA] AUTO-PERFORMANCE MODE ACTIVATED (TPS: {}). Reducing entities and particles.",
                    String.format("%.2f", tps));
            // Signal performance engine to reduce load
        } else if (autoPerformanceActive && tps >= 19.5) {
            autoPerformanceActive = false;
            LOGGER.info("[LEGA] AUTO-PERFORMANCE MODE DEACTIVATED. TPS restored to {}.",
                    String.format("%.2f", tps));
        }
    }

    // =========================================================================
    // Public metrics
    // =========================================================================

    
    public double[] getTPS() {
        return new double[]{
                avg(tps1Min),
                avg(tps5Min),
                avg(tps15Min)
        };
    }

    public double getLastMspt()  { return lastMspt; }
    public double getAvgMspt()   { return avgMspt; }
    public double getMaxMspt()   { return maxMspt; }
    public long   getTickCount() { return tickCount.get(); }

    private double avg(long[] arr) {
        long sum = 0;
        for (long v : arr) sum += v;
        return (double) sum / arr.length;
    }
}

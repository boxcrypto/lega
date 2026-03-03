package net.lega.performance;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public final class LegaPerformanceEngine {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Performance");

    private final AtomicBoolean autoPerformanceActive = new AtomicBoolean(false);
    private final AtomicInteger performanceScore = new AtomicInteger(100);
    private final AtomicLong totalGcPauseMs = new AtomicLong(0);

    private final EntityOptimizer entityOptimizer;
    private final ChunkOptimizer chunkOptimizer;
    private final TickOptimizer tickOptimizer;
    private final GcMonitor gcMonitor;
    private final AIServerOptimizer aiOptimizer;

    private final ScheduledExecutorService monitorPool;

    public LegaPerformanceEngine() {
        this.entityOptimizer = new EntityOptimizer();
        this.chunkOptimizer  = new ChunkOptimizer();
        this.tickOptimizer   = new TickOptimizer();
        this.gcMonitor       = new GcMonitor();
        this.aiOptimizer     = new AIServerOptimizer();

        this.monitorPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LEGA-PerfMonitor");
            t.setDaemon(true);
            return t;
        });
    }

    
    public void initialize() {
        LOGGER.info("[LEGA/Performance] Initializing performance engine...");

        entityOptimizer.initialize();
        chunkOptimizer.initialize();
        tickOptimizer.initialize();
        gcMonitor.initialize();
        aiOptimizer.initialize();

        // Schedule recurring performance score update
        monitorPool.scheduleAtFixedRate(this::updatePerformanceScore, 5, 5, TimeUnit.SECONDS);

        // Schedule AI optimizer update (learns from patterns)
        monitorPool.scheduleAtFixedRate(aiOptimizer::analyze, 60, 60, TimeUnit.SECONDS);

        LOGGER.info("[LEGA/Performance] Performance engine ready.");
    }

    
    public void shutdown() {
        monitorPool.shutdownNow();
        LOGGER.info("[LEGA/Performance] Performance engine stopped.");
    }

    // =========================================================================
    // Auto-Performance Mode
    // =========================================================================

    
    public void onTick(double currentTps) {
        if (currentTps < 17.0 && !autoPerformanceActive.get()) {
            activateAutoPerformanceMode(currentTps);
        } else if (currentTps >= 19.5 && autoPerformanceActive.get()) {
            deactivateAutoPerformanceMode(currentTps);
        }
    }

    private void activateAutoPerformanceMode(double tps) {
        autoPerformanceActive.set(true);
        LOGGER.warn("[LEGA/Performance] AUTO-PERFORMANCE MODE: ON (TPS={})", String.format("%.2f", tps));

        entityOptimizer.setEmergencyMode(true);
        chunkOptimizer.setThrottleLevel(ChunkOptimizer.ThrottleLevel.AGGRESSIVE);
        tickOptimizer.enableDynamicSkipping(true);

        // Reduce particle effects
        // Reduce entity spawn rates
        // Adjust view distance dynamically
    }

    private void deactivateAutoPerformanceMode(double tps) {
        autoPerformanceActive.set(false);
        LOGGER.info("[LEGA/Performance] AUTO-PERFORMANCE MODE: OFF (TPS={})", String.format("%.2f", tps));

        entityOptimizer.setEmergencyMode(false);
        chunkOptimizer.setThrottleLevel(ChunkOptimizer.ThrottleLevel.NORMAL);
        tickOptimizer.enableDynamicSkipping(false);
    }

    // =========================================================================
    // Performance Score
    // =========================================================================

    
    public int getPerformanceScore() { return performanceScore.get(); }
    public boolean isAutoPerformanceActive() { return autoPerformanceActive.get(); }
    public EntityOptimizer getEntityOptimizer() { return entityOptimizer; }
    public ChunkOptimizer getChunkOptimizer() { return chunkOptimizer; }
    public TickOptimizer getTickOptimizer() { return tickOptimizer; }
    public GcMonitor getGcMonitor() { return gcMonitor; }
    public AIServerOptimizer getAIOptimizer() { return aiOptimizer; }
}

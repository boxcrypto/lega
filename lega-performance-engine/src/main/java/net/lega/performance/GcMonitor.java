package net.lega.performance;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


public final class GcMonitor {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/GC");

    private static final long WARN_PAUSE_MS = 50;  // Warn if GC pause > 50ms
    private static final double HEAP_WARN_PERCENT = 0.85; // Warn if heap > 85%

    private final AtomicLong totalGcCount = new AtomicLong(0);
    private final AtomicLong totalGcPauseMs = new AtomicLong(0);
    private final AtomicLong maxGcPauseMs = new AtomicLong(0);
    private long lastGcCount = 0;

    void initialize() {
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener(this::onGcNotification, null, null);
            }
        }

        // Set memory alert thresholds on heap pools
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.isUsageThresholdSupported()) {
                long max = pool.getUsage().getMax();
                if (max > 0) {
                    pool.setUsageThreshold((long)(max * HEAP_WARN_PERCENT));
                }
            }
        }

        LOGGER.info("[LEGA/GC] GC monitor initialized. Pause threshold: {}ms, Heap warn: {}%",
                WARN_PAUSE_MS, (int)(HEAP_WARN_PERCENT * 100));
    }

    private void onGcNotification(Notification notification, Object handback) {
        String type = notification.getType();

        if (type.equals("com.sun.management.gc.notification")) {
            com.sun.management.GarbageCollectionNotificationInfo info =
                    com.sun.management.GarbageCollectionNotificationInfo
                            .from((javax.management.openmbean.CompositeData) notification.getUserData());

            long pauseMs = info.getGcInfo().getDuration();
            totalGcCount.incrementAndGet();
            totalGcPauseMs.addAndGet(pauseMs);

            if (pauseMs > maxGcPauseMs.get()) {
                maxGcPauseMs.set(pauseMs);
            }

            if (pauseMs >= WARN_PAUSE_MS) {
                LOGGER.warn("[LEGA/GC] Long GC pause detected: {}ms ({})", pauseMs, info.getGcName());
                LOGGER.warn("[LEGA/GC] Consider adjusting GC flags or reducing heap usage.");
            } else {
                LOGGER.debug("[LEGA/GC] GC pause: {}ms ({})", pauseMs, info.getGcName());
            }
        }
    }

    
    public void logSummary() {
        long count = totalGcCount.get();
        long total = totalGcPauseMs.get();
        long max   = maxGcPauseMs.get();
        long avg   = count > 0 ? total / count : 0;

        Runtime rt = Runtime.getRuntime();
        long usedMb  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb   = rt.maxMemory() / (1024 * 1024);

        LOGGER.info("[LEGA/GC] GC Summary: count={} totalPause={}ms avgPause={}ms maxPause={}ms heap={}/{}MB",
                count, total, avg, max, usedMb, maxMb);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public long getTotalGcCount()    { return totalGcCount.get(); }
    public long getTotalGcPauseMs()  { return totalGcPauseMs.get(); }
    public long getMaxGcPauseMs()    { return maxGcPauseMs.get(); }
    public long getAverageGcPauseMs() {
        long count = totalGcCount.get();
        return count > 0 ? totalGcPauseMs.get() / count : 0;
    }
}

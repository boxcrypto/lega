package net.lega.performance;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public final class AIServerOptimizer {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/AIOptimizer");

    // Rolling hourly player counts (24 hours)
    private final int[] hourlyPlayerCounts = new int[24];
    private final AtomicInteger[] hourlyTickCount = new AtomicInteger[24];

    // Chunk heatmap: chunkKey -> access count
    private final ConcurrentHashMap<Long, AtomicInteger> chunkHeatmap = new ConcurrentHashMap<>();

    // Plugin lag map: pluginName -> accumulated lag ms
    private final ConcurrentHashMap<String, Long> pluginLagMap = new ConcurrentHashMap<>();

    // Detected patterns
    private final List<String> detectedPatterns = Collections.synchronizedList(new ArrayList<>());

    public AIServerOptimizer() {
        for (int i = 0; i < 24; i++) {
            hourlyTickCount[i] = new AtomicInteger(0);
        }
    }

    void initialize() {
        LOGGER.info("[LEGA/AI] AI Server Optimizer initialized. Learning period: 1 hour.");
    }

    
    public void recordPlayerCount(int hour, int online) {
        hourlyPlayerCounts[hour] = (hourlyPlayerCounts[hour] + online) / 2; // rolling avg
    }

    
    public void recordChunkAccess(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        chunkHeatmap.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    
    public void recordPluginLag(String pluginName, long lagMs) {
        pluginLagMap.merge(pluginName, lagMs, Long::sum);
    }

    private void detectPeakHours() {
        int maxPlayers = 0;
        int peakHour = -1;
        for (int i = 0; i < 24; i++) {
            if (hourlyPlayerCounts[i] > maxPlayers) {
                maxPlayers = hourlyPlayerCounts[i];
                peakHour = i;
            }
        }
        if (peakHour >= 0 && maxPlayers > 0) {
            detectedPatterns.removeIf(p -> p.startsWith("Peak hour:"));
            detectedPatterns.add("Peak hour: " + peakHour + ":00 avg=" + maxPlayers + " players");
        }
    }

    private void analyzeChunkHeatmap() {
        // Find top 5 hottest chunks
        chunkHeatmap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(5)
                .forEach(entry -> {
                    int x = (int)(entry.getKey() >> 32);
                    int z = (int)(entry.getKey().longValue());
                    LOGGER.debug("[LEGA/AI] Hot chunk [{},{}] accessed {} times", x, z, entry.getValue().get());
                });
    }

    private void detectLaggingPlugins() {
        pluginLagMap.entrySet().stream()
                .filter(e -> e.getValue() > 1000) // > 1 second total lag
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    LOGGER.warn("[LEGA/AI] Plugin '{}' has caused {}ms total lag. Consider reviewing.",
                            e.getKey(), e.getValue());
                });
    }

    private void logInsights() {
        if (!detectedPatterns.isEmpty()) {
            LOGGER.info("[LEGA/AI] Detected patterns: {}", detectedPatterns);
        }
    }

    
    public boolean isCurrentlyPeakHour() {
        int hour = LocalTime.now().getHour();
        int max = Arrays.stream(hourlyPlayerCounts).max().getAsInt();
        return max > 0 && hourlyPlayerCounts[hour] >= max * 0.8;
    }

    public List<String> getDetectedPatterns() {
        return Collections.unmodifiableList(detectedPatterns);
    }

    public Map<String, Long> getPluginLagSummary() {
        return Collections.unmodifiableMap(pluginLagMap);
    }
}

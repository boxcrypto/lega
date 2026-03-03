package net.lega.profiler;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;


public final class LegaProfiler {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Profiler");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long startTime;
    private volatile long stopTime;

    // Plugin CPU tracking: pluginName -> nanoseconds
    private final ConcurrentHashMap<String, AtomicLong> pluginCpuNanos = new ConcurrentHashMap<>();

    // Tick breakdown tracking
    private final ConcurrentHashMap<String, AtomicLong> tickPhaseNanos = new ConcurrentHashMap<>();

    // Chunk access heatmap: chunkKey -> count
    private final ConcurrentHashMap<Long, AtomicInteger> chunkHeatmap = new ConcurrentHashMap<>();

    // Entity type memory estimates
    private final ConcurrentHashMap<String, AtomicInteger> entityCounts = new ConcurrentHashMap<>();

    // Tick samples
    private final List<TickSample> tickSamples = Collections.synchronizedList(new ArrayList<>());

    // Output dir
    private final Path outputDir;

    public LegaProfiler() {
        this(Path.of("lega-profiler-output"));
    }

    public LegaProfiler(Path outputDir) {
        this.outputDir = outputDir;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTime = System.nanoTime();
            pluginCpuNanos.clear();
            tickPhaseNanos.clear();
            chunkHeatmap.clear();
            entityCounts.clear();
            tickSamples.clear();
            LOGGER.info("[LEGA/Profiler] Profiler started.");
        } else {
            LOGGER.warn("[LEGA/Profiler] Profiler is already running.");
        }
    }

    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            stopTime = System.nanoTime();
            LOGGER.info("[LEGA/Profiler] Profiler stopped. Total time: {}ms",
                    (stopTime - startTime) / 1_000_000);
        }
    }

    
    public boolean isRunning() { return running.get(); }

    // =========================================================================
    // Data recording
    // =========================================================================

    
    public void recordPluginCpu(String pluginName, long nanos) {
        if (!running.get()) return;
        pluginCpuNanos.computeIfAbsent(pluginName, k -> new AtomicLong(0)).addAndGet(nanos);
    }

    
    public void recordTickPhase(String phase, long nanos) {
        if (!running.get()) return;
        tickPhaseNanos.computeIfAbsent(phase, k -> new AtomicLong(0)).addAndGet(nanos);
    }

    
    public void recordChunkAccess(int chunkX, int chunkZ) {
        if (!running.get()) return;
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        chunkHeatmap.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    
    public void recordTick(long tickId, double durationMs) {
        if (!running.get()) return;
        tickSamples.add(new TickSample(tickId, durationMs, System.currentTimeMillis()));
        // Keep last 1200 ticks (1 minute)
        if (tickSamples.size() > 1200) {
            tickSamples.remove(0);
        }
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    
    public ProfilerReport generateReport() {
        Runtime rt = Runtime.getRuntime();
        long usedMb  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb   = rt.maxMemory() / (1024 * 1024);

        // Top 10 plugins by CPU usage
        List<PluginCpuEntry> topPlugins = pluginCpuNanos.entrySet().stream()
                .map(e -> new PluginCpuEntry(e.getKey(), e.getValue().get()))
                .sorted((a, b) -> Long.compare(b.nanos(), a.nanos()))
                .limit(10)
                .toList();

        // Tick stats
        double avgTick = tickSamples.isEmpty() ? 0 :
                tickSamples.stream().mapToDouble(TickSample::durationMs).average().orElse(0);
        double maxTick = tickSamples.isEmpty() ? 0 :
                tickSamples.stream().mapToDouble(TickSample::durationMs).max().orElse(0);

        // GC stats
        long gcCount = 0, gcPauseMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount   += gc.getCollectionCount();
            gcPauseMs += gc.getCollectionTime();
        }

        // Top hot chunks
        List<ChunkHotspot> hotChunks = chunkHeatmap.entrySet().stream()
                .map(e -> {
                    int x = (int)(e.getKey() >> 32);
                    int z = (int)(e.getKey().longValue());
                    return new ChunkHotspot(x, z, e.getValue().get());
                })
                .sorted((a, b) -> Integer.compare(b.accessCount(), a.accessCount()))
                .limit(10)
                .toList();

        long durationMs = running.get()
                ? (System.nanoTime() - startTime) / 1_000_000
                : (stopTime - startTime) / 1_000_000;

        return new ProfilerReport(
                Instant.now().toString(),
                durationMs,
                usedMb,
                maxMb,
                avgTick,
                maxTick,
                gcCount,
                gcPauseMs,
                topPlugins,
                hotChunks,
                new ArrayList<>(tickSamples)
        );
    }

    
    public Path exportReport() throws IOException {
        Files.createDirectories(outputDir);
        String timestamp = TS_FMT.format(Instant.now());
        Path output = outputDir.resolve("lega-profiler-" + timestamp + ".json");

        ProfilerReport report = generateReport();
        try (Writer writer = Files.newBufferedWriter(output)) {
            GSON.toJson(report, writer);
        }

        LOGGER.info("[LEGA/Profiler] Report exported to: {}", output.toAbsolutePath());
        return output;
    }

    
    public String getTextSummary() {
        ProfilerReport report = generateReport();
        StringBuilder sb = new StringBuilder();
        sb.append("=== LEGA Profiler Report ===\n");
        sb.append(String.format("Profiling time  : %dms%n", report.durationMs()));
        sb.append(String.format("Memory          : %dMB / %dMB%n", report.usedMemoryMb(), report.maxMemoryMb()));
        sb.append(String.format("Avg tick        : %.2fms | Max tick: %.2fms%n", report.avgTickMs(), report.maxTickMs()));
        sb.append(String.format("GC stats        : %d collections, %dms total pause%n", report.gcCount(), report.gcPauseMs()));
        sb.append("\nTop Plugins by CPU:\n");
        report.topPluginsByCpu().forEach(p ->
                sb.append(String.format("  %-30s %6.2fms%n", p.pluginName(), p.nanos() / 1_000_000.0))
        );
        sb.append("\nTop Hot Chunks:\n");
        report.topHotChunks().forEach(c ->
                sb.append(String.format("  [%6d, %6d] – %d accesses%n", c.chunkX(), c.chunkZ(), c.accessCount()))
        );
        return sb.toString();
    }

    // =========================================================================
    // Records
    // =========================================================================

    public record TickSample(long tickId, double durationMs, long timestamp) {}
    public record PluginCpuEntry(String pluginName, long nanos) {}
    public record ChunkHotspot(int chunkX, int chunkZ, int accessCount) {}

    public record ProfilerReport(
            String timestamp,
            long durationMs,
            long usedMemoryMb,
            long maxMemoryMb,
            double avgTickMs,
            double maxTickMs,
            long gcCount,
            long gcPauseMs,
            List<PluginCpuEntry> topPluginsByCpu,
            List<ChunkHotspot> topHotChunks,
            List<TickSample> tickSamples
    ) {}
}

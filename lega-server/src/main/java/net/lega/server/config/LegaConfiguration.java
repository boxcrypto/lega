package net.lega.server.config;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;


public final class LegaConfiguration {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Config");

    private final Path configDir;
    private Map<String, Object> legaConfig        = new LinkedHashMap<>();
    private Map<String, Object> performanceConfig  = new LinkedHashMap<>();
    private Map<String, Object> securityConfig     = new LinkedHashMap<>();
    private Map<String, Object> velocityConfig     = new LinkedHashMap<>();

    public LegaConfiguration() {
        this(Path.of("."));
    }

    public LegaConfiguration(Path serverRoot) {
        this.configDir = serverRoot;
    }

    
    public void load() throws IOException {
        legaConfig       = loadOrCreate("lega.yml",        buildDefaultLegaConfig());
        performanceConfig = loadOrCreate("performance.yml", buildDefaultPerformanceConfig());
        securityConfig   = loadOrCreate("security.yml",    buildDefaultSecurityConfig());
        velocityConfig   = loadOrCreate("velocity.yml",    buildDefaultVelocityConfig());
        LOGGER.info("Configuration loaded successfully.");
    }

    
    public void reload() throws IOException {
        LOGGER.info("Reloading configuration...");
        load();
        LOGGER.info("Configuration reloaded.");
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public boolean isVelocityEnabled() {
        return (boolean) velocityConfig.getOrDefault("enabled", false);
    }

    public String getVelocitySecret() {
        return (String) velocityConfig.getOrDefault("forwarding-secret", "changeme");
    }

    public int getServerPort() {
        return (int) legaConfig.getOrDefault("port", 25565);
    }

    public int getMaxPlayers() {
        return (int) legaConfig.getOrDefault("max-players", 200);
    }

    public boolean isAutoPerformanceModeEnabled() {
        return (boolean) performanceConfig.getOrDefault("auto-performance-mode", true);
    }

    public int getEntityActivationRange() {
        return (int) performanceConfig.getOrDefault("entity-activation-range", 32);
    }

    public boolean isAsyncChunkLoadEnabled() {
        return (boolean) performanceConfig.getOrDefault("async-chunk-load", true);
    }

    public boolean isRegionizedTickEnabled() {
        return (boolean) performanceConfig.getOrDefault("regionized-tick", false);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String file, String key, T defaultValue) {
        Map<String, Object> cfg = switch (file) {
            case "lega"        -> legaConfig;
            case "performance" -> performanceConfig;
            case "security"    -> securityConfig;
            case "velocity"    -> velocityConfig;
            default -> throw new IllegalArgumentException("Unknown config file: " + file);
        };
        return (T) cfg.getOrDefault(key, defaultValue);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadOrCreate(String fileName, Map<String, Object> defaults) throws IOException {
        Path file = configDir.resolve(fileName);
        if (!Files.exists(file)) {
            LOGGER.info("Creating default config: {}", fileName);
            Files.createDirectories(file.getParent());
            save(file, defaults);
            return new LinkedHashMap<>(defaults);
        }

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(opts);

        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> loaded = yaml.load(reader);
            if (loaded == null) loaded = new LinkedHashMap<>();
            // Merge defaults for any missing keys
            for (Map.Entry<String, Object> entry : defaults.entrySet()) {
                loaded.putIfAbsent(entry.getKey(), entry.getValue());
            }
            return loaded;
        }
    }

    private void save(Path file, Map<String, Object> data) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);
        try (Writer writer = Files.newBufferedWriter(file)) {
            yaml.dump(data, writer);
        }
    }

    // =========================================================================
    // Default config builders
    // =========================================================================

    private Map<String, Object> buildDefaultLegaConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("# LEGA Server Configuration", null);
        m.put("port", 25565);
        m.put("max-players", 200);
        m.put("motd", "&5&lLEGA &8» &7High Performance Minecraft");
        m.put("online-mode", false); // false when behind Velocity
        m.put("compression-threshold", 256);
        m.put("proxy-protocol", false);
        m.put("bungeecord", false);
        m.put("log-commands", true);
        m.put("console-has-color", true);
        return m;
    }

    private Map<String, Object> buildDefaultPerformanceConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("# LEGA Performance Tuning", null);
        m.put("auto-performance-mode", true);
        m.put("target-tps", 20);
        m.put("async-chunk-load", true);
        m.put("async-lighting", true);
        m.put("regionized-tick", false);
        m.put("entity-activation-range", 32);
        m.put("entity-merge-radius", 2.5);
        m.put("max-entity-cramming", 24);
        m.put("disable-entity-ai-in-empty-chunks", true);
        m.put("async-pathfinding", true);
        m.put("chunk-tick-throttle", true);
        m.put("dynamic-tick-skip", true);
        m.put("adaptive-entity-processing", true);
        m.put("particle-reduction-on-lag", true);
        m.put("ai-optimizer-enabled", true);
        m.put("multi-core-region-engine", false);
        m.put("gc-monitor-enabled", true);
        m.put("gc-memory-alert-threshold-percent", 85);
        return m;
    }

    private Map<String, Object> buildDefaultSecurityConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("# LEGA Security Configuration", null);
        m.put("anti-crash", true);
        m.put("anti-packet-exploit", true);
        m.put("rate-limiter", true);
        m.put("rate-limit-packets-per-second", 300);
        m.put("anti-bot", true);
        m.put("anti-bot-threshold", 5);
        m.put("packet-firewall", true);
        m.put("book-ban-protection", true);
        m.put("chunk-exploit-protection", true);
        m.put("flood-detection", true);
        m.put("flood-threshold-connections-per-second", 10);
        m.put("blocked-ips", java.util.List.of());
        m.put("security-log", true);
        return m;
    }

    private Map<String, Object> buildDefaultVelocityConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("# LEGA Velocity Bridge Configuration", null);
        m.put("enabled", false);
        m.put("forwarding-mode", "MODERN"); // MODERN, LEGACY, NONE
        m.put("forwarding-secret", "CHANGE_ME_PLEASE");
        m.put("cross-server-messaging", true);
        m.put("cross-server-teleport", true);
        m.put("sync-permissions", true);
        m.put("sync-staff-mode", true);
        m.put("encrypted-bridge", true);
        m.put("anti-spoofing", true);
        return m;
    }
}

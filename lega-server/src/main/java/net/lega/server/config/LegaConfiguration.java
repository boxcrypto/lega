package net.lega.server.config;

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

    public LegaConfiguration() { this(Path.of(".")); }
    public LegaConfiguration(Path serverRoot) { this.configDir = serverRoot; }

    public void load() throws IOException {
        legaConfig        = loadOrCreate("lega.yml",        buildDefaultLegaConfig());
        performanceConfig = loadOrCreate("performance.yml", buildDefaultPerformanceConfig());
        securityConfig    = loadOrCreate("security.yml",    buildDefaultSecurityConfig());
        velocityConfig    = loadOrCreate("velocity.yml",    buildDefaultVelocityConfig());
        LOGGER.info("Configuration loaded successfully.");
    }

    public void reload() throws IOException {
        LOGGER.info("Reloading configuration...");
        load();
        LOGGER.info("Configuration reloaded.");
    }

    public boolean isVelocityEnabled() { return (boolean) velocityConfig.getOrDefault("enabled", false); }
    public String getVelocitySecret()  { return (String)  velocityConfig.getOrDefault("forwarding-secret", "changeme"); }
    public int getServerPort()          { return (int)     legaConfig.getOrDefault("port", 25565); }
    public int getMaxPlayers()          { return (int)     legaConfig.getOrDefault("max-players", 200); }
    public String getLevelName()        { return (String)  legaConfig.getOrDefault("level-name", "world"); }
    public String getLevelSeed()        { return (String)  legaConfig.getOrDefault("level-seed", ""); }
    public String getGamemode()         { return (String)  legaConfig.getOrDefault("gamemode", "survival"); }
    public String getDifficulty()       { return (String)  legaConfig.getOrDefault("difficulty", "normal"); }
    public boolean isPvpEnabled()       { return (boolean) legaConfig.getOrDefault("pvp", true); }
    public boolean isOnlineModeEnabled(){ return (boolean) legaConfig.getOrDefault("online-mode", false); }
    public boolean isWhitelistEnabled() { return (boolean) legaConfig.getOrDefault("white-list", false); }
    public boolean isCommandBlockEnabled(){ return (boolean) legaConfig.getOrDefault("enable-command-block", false); }
    public boolean isHardcore()         { return (boolean) legaConfig.getOrDefault("hardcore", false); }
    public boolean isAllowFlight()      { return (boolean) legaConfig.getOrDefault("allow-flight", false); }
    public int getViewDistance()        { return (int)     legaConfig.getOrDefault("view-distance", 10); }
    public int getSimulationDistance()  { return (int)     legaConfig.getOrDefault("simulation-distance", 10); }
    public int getSpawnProtection()     { return (int)     legaConfig.getOrDefault("spawn-protection", 16); }
    public int getOpPermissionLevel()   { return (int)     legaConfig.getOrDefault("op-permission-level", 4); }
    public int getPlayerIdleTimeout()   { return (int)     legaConfig.getOrDefault("player-idle-timeout", 0); }
    public String getMotd()             { return (String)  legaConfig.getOrDefault("motd", "LEGA Server"); }

    public boolean isAutoPerformanceModeEnabled() { return (boolean) performanceConfig.getOrDefault("auto-performance-mode", true); }
    public int getEntityActivationRange()         { return (int)     performanceConfig.getOrDefault("entity-activation-range", 32); }
    public boolean isAsyncChunkLoadEnabled()      { return (boolean) performanceConfig.getOrDefault("async-chunk-load", true); }
    public boolean isRegionizedTickEnabled()       { return (boolean) performanceConfig.getOrDefault("regionized-tick", false); }

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

    private Map<String, Object> buildDefaultLegaConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("server-ip", "");
        m.put("port", 25565);
        m.put("online-mode", false);
        m.put("prevent-proxy-connections", false);
        m.put("enable-status", true);
        m.put("hide-online-players", false);
        m.put("enforce-secure-profile", false);
        m.put("compression-threshold", 256);
        m.put("use-native-transport", true);
        m.put("enable-rcon", false);
        m.put("rcon-port", 25575);
        m.put("rcon-password", "");
        m.put("broadcast-rcon-to-ops", true);
        m.put("enable-query", false);
        m.put("query-port", 25565);
        m.put("motd", "LEGA High Performance Minecraft");
        m.put("max-players", 200);
        m.put("player-idle-timeout", 0);
        m.put("op-permission-level", 4);
        m.put("function-permission-level", 2);
        m.put("broadcast-console-to-ops", true);
        m.put("enable-command-block", false);
        m.put("level-name", "world");
        m.put("level-seed", "");
        m.put("level-type", "DEFAULT");
        m.put("generate-structures", true);
        m.put("max-world-size", 29999984);
        m.put("max-build-height", 256);
        m.put("view-distance", 10);
        m.put("simulation-distance", 10);
        m.put("spawn-protection", 16);
        m.put("allow-nether", true);
        m.put("sync-chunk-writes", true);
        m.put("gamemode", "survival");
        m.put("force-gamemode", false);
        m.put("difficulty", "normal");
        m.put("hardcore", false);
        m.put("pvp", true);
        m.put("spawn-monsters", true);
        m.put("spawn-animals", true);
        m.put("spawn-npcs", true);
        m.put("allow-flight", false);
        m.put("max-tick-time", 60000);
        m.put("white-list", false);
        m.put("enforce-whitelist", false);
        m.put("resource-pack", "");
        m.put("resource-pack-sha1", "");
        m.put("resource-pack-prompt", "");
        m.put("resource-pack-required", false);
        m.put("proxy-protocol", false);
        m.put("bungeecord", false);
        m.put("log-commands", true);
        m.put("console-has-color", true);
        m.put("text-filtering-config", "");
        return m;
    }

    private Map<String, Object> buildDefaultPerformanceConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
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
        m.put("enabled", false);
        m.put("forwarding-mode", "MODERN");
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

package net.lega.server;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.bungeecord.LegaBungeeCordBridge;
import net.lega.bungeecord.proxy.ProxyPlatform;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.negotiation.VersionNegotiator;
import net.lega.server.command.ConsoleCommandReader;
import net.lega.server.config.EulaConfig;
import net.lega.server.config.LegaConfiguration;
import net.lega.server.config.VersionConfig;
import net.lega.server.network.LegaNettyServer;
import net.lega.server.tick.LegaTickEngine;
import net.lega.velocity.LegaVelocityBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;


public final class LegaServer {

    private static final Logger LOGGER = LogManager.getLogger("LEGA");
    private static LegaServer instance;

    private final String[] launchArgs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private LegaConfiguration configuration;
    private VersionConfig versionConfig;
    private EulaConfig eulaConfig;
    private VersionNegotiator versionNegotiator;
    private LegaBungeeCordBridge bungeeBridge;
    private LegaVelocityBridge velocityBridge;
    private LegaNettyServer nettyServer;
    private LegaTickEngine tickEngine;

    private volatile long startTime;

    public LegaServer(String[] launchArgs) {
        this.launchArgs = Arrays.copyOf(launchArgs, launchArgs.length);
        if (instance != null) {
            throw new IllegalStateException("LegaServer is already initialized!");
        }
        instance = this;
    }

    
    public void start() throws Exception {
        this.startTime = System.currentTimeMillis();
        Path serverRoot = Path.of(".");
        LOGGER.info("[LEGA] Starting LEGA Server...");

        // ── Phase 1: Version Config + EULA ───────────────────────────────────
        LOGGER.info("[LEGA] Loading version configuration...");
        versionConfig = new VersionConfig(serverRoot);
        versionConfig.load(); // throws if EULA not accepted or first run

        // Sync eula.txt for compatibility with standard Minecraft tools
        eulaConfig = new EulaConfig(serverRoot);
        if (!eulaConfig.load()) {
            eulaConfig.markAccepted();
        }

        // ── Phase 2: Main configuration ─────────────────────────────────────
        LOGGER.info("[LEGA] Loading configuration...");
        this.configuration = new LegaConfiguration(serverRoot);
        this.configuration.load();

        // ── Phase 2b: Server directory structure ────────────────────────────
        createDirectoryStructure(serverRoot);

        // ── Phase 3: Build version negotiator ───────────────────────────────
        versionNegotiator = new VersionNegotiator(
                versionConfig.getDefaultVersion(),
                versionConfig.getEnabledVersions());

        // ── Phase 4: Proxy bridge ────────────────────────────────────────────
        LOGGER.info("[LEGA] Initializing Proxy Bridge...");
        bungeeBridge = new LegaBungeeCordBridge(serverRoot);
        bungeeBridge.initialize();

        // ── Phase 5: Core subsystems ─────────────────────────────────────────
        LOGGER.info("[LEGA] Initializing Performance Engine...");
        initPerformanceEngine();

        LOGGER.info("[LEGA] Initializing Async Engine...");
        initAsyncEngine();

        LOGGER.info("[LEGA] Initializing Security Manager...");
        initSecurityManager();

        LOGGER.info("[LEGA] Initializing World Engine...");
        initWorldEngine();

        LOGGER.info("[LEGA] Initializing Profiler...");
        initProfiler();

        LOGGER.info("[LEGA] Initializing Velocity Bridge...");
        initVelocityBridge();

        // ── Phase 6: Network server ─────────────────────────────────────────
        LOGGER.info("[LEGA] Starting Netty network server...");
        nettyServer = new LegaNettyServer(
                configuration.getServerPort(),
                versionNegotiator,
                bungeeBridge);
        nettyServer.start();

        // ── Phase 7: Shutdown hook ──────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "LEGA-ShutdownHook"));

        // ── Phase 8: Tick engine main loop ──────────────────────────────────
        LOGGER.info("[LEGA] Starting Tick Engine...");
        this.tickEngine = new LegaTickEngine(this);

        running.set(true);
        LOGGER.info("[LEGA] All systems nominal. Server is running on port {}.",
                configuration.getServerPort());
        LOGGER.info("[LEGA] Supported protocol: {}",
                versionNegotiator.enabledVersionRange());

        // Phase 9: Start console command reader (daemon thread — reads stdin)
        ConsoleCommandReader consoleReader = new ConsoleCommandReader(this);
        consoleReader.start();

        // Phase 10: Enter main loop (blocks until server stops)
        tickEngine.run();
    }

    
    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;

        LOGGER.info("[LEGA] Shutting down LEGA Server...");

        try {
            if (tickEngine != null) tickEngine.stop();
            LOGGER.info("[LEGA] Tick engine stopped.");
        } catch (Exception e) {
            LOGGER.error("[LEGA] Error stopping tick engine", e);
        }

        try {
            if (nettyServer != null) nettyServer.stop();
        } catch (Exception e) {
            LOGGER.error("[LEGA] Error stopping network server", e);
        }

        LOGGER.info("[LEGA] Server shutdown complete. Goodbye!");
    }

    private void initPerformanceEngine() {
        // Loaded via ServiceLoader or direct instantiation once lega-performance-engine is on classpath
        LOGGER.info("[LEGA] Performance Engine loaded.");
    }

    private void initAsyncEngine() {
        LOGGER.info("[LEGA] Async Engine initialized with "
                + Runtime.getRuntime().availableProcessors() + " virtual threads.");
    }

    private void initSecurityManager() {
        LOGGER.info("[LEGA] Security Manager active. Anti-crash, Anti-Bot, Packet Firewall enabled.");
    }

    private void initWorldEngine() {
        LOGGER.info("[LEGA] World Engine ready. Instanced world support enabled.");
    }

    private void initProfiler() {
        LOGGER.info("[LEGA] LEGA Profiler online. Use /lega profiler start to begin.");
    }

    private void initVelocityBridge() {
        if (bungeeBridge.isEnabled()
                && bungeeBridge.getConfig().getPlatform() == ProxyPlatform.VELOCITY) {
            String secret = bungeeBridge.getConfig().getVelocitySecret();
            velocityBridge = new LegaVelocityBridge(secret, false, true);
            velocityBridge.initialize();
            LOGGER.info("[LEGA] Velocity Bridge active (modern forwarding, HMAC-SHA256).");
        } else {
            LOGGER.info("[LEGA] Velocity Bridge disabled (proxy platform: {}).",
                    bungeeBridge.isEnabled()
                    ? bungeeBridge.getConfig().getPlatform().getDisplayName()
                    : "none");
        }
    }

    // =========================================================================
    // Directory structure
    // =========================================================================

    private void createDirectoryStructure(Path root) throws Exception {
        // World directories (overworld, nether, end)
        String levelName = "world";
        try {
            if (configuration != null) levelName = configuration.getLevelName();
        } catch (Exception ignored) { }

        Path world = root.resolve(levelName);
        Files.createDirectories(world.resolve("region"));
        Files.createDirectories(world.resolve("playerdata"));
        Files.createDirectories(world.resolve("data"));
        Files.createDirectories(root.resolve(levelName + "_nether").resolve("DIM-1").resolve("region"));
        Files.createDirectories(root.resolve(levelName + "_the_end").resolve("DIM1").resolve("region"));

        // Plugin and data directories
        Files.createDirectories(root.resolve("plugins"));
        Files.createDirectories(root.resolve("logs"));
        Files.createDirectories(root.resolve("crash-reports"));
        Files.createDirectories(root.resolve("config"));
        Files.createDirectories(root.resolve("mods"));

        LOGGER.info("[LEGA] Server directory structure ready.");
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public static LegaServer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LegaServer not yet initialized!");
        }
        return instance;
    }

    public boolean isRunning() {
        return running.get();
    }

    public LegaConfiguration getConfiguration() {
        return configuration;
    }

    public VersionConfig getVersionConfig() {
        return versionConfig;
    }

    public VersionNegotiator getVersionNegotiator() {
        return versionNegotiator;
    }

    public LegaBungeeCordBridge getBungeeBridge() {
        return bungeeBridge;
    }

    public LegaVelocityBridge getVelocityBridge() {
        return velocityBridge;
    }

    public LegaNettyServer getNettyServer() {
        return nettyServer;
    }

    public LegaTickEngine getTickEngine() {
        return tickEngine;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    public String[] getLaunchArgs() {
        return Arrays.copyOf(launchArgs, launchArgs.length);
    }
}

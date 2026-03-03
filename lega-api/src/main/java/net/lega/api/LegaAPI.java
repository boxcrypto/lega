package net.lega.api;

import net.lega.api.event.LegaEventBus;
import net.lega.api.network.LegaNetworkManager;
import net.lega.api.player.LegaPlayerManager;
import net.lega.api.scheduler.LegaScheduler;
import net.lega.api.server.LegaServerInfo;
import net.lega.api.world.LegaWorldManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/**
 * LEGA Server API - Main entry point for plugin developers.
 *
 * <p>LEGA is a high-performance Minecraft server software designed for extreme
 * scalability and performance. This API provides access to all LEGA systems.</p>
 *
 * <p>Compatible with Bukkit, Spigot and Paper plugins.</p>
 *
 * @author maatsuh
 * @version 1.0.0
 * @since Minecraft 1.7.10 - 1.21.x
 *
 * <h2>Available systems</h2>
 * <ul>
 *   <li>{@link #getEventBus()} — high-performance event bus (lambda dispatch)</li>
 *   <li>{@link #getScheduler()} — hybrid sync/async scheduler (Virtual Threads)</li>
 *   <li>{@link #getWorldManager()} — RAM-based instanced world engine</li>
 *   <li>{@link #getPlayerManager()} — connected player registry and broadcast</li>
 *   <li>{@link #getNetworkManager()} — raw protocol access and network diagnostics</li>
 *   <li>{@link #getServerInfo()} — real-time TPS / MSPT / memory metrics</li>
 * </ul>
 */
public final class LegaAPI {

    private static LegaAPI instance;

    private final LegaEventBus eventBus;
    private final LegaScheduler scheduler;
    private final LegaWorldManager worldManager;
    private final LegaPlayerManager playerManager;
    private final LegaNetworkManager networkManager;
    private final LegaServerInfo serverInfo;
    private final Logger logger;

    private LegaAPI(
            @NotNull LegaEventBus eventBus,
            @NotNull LegaScheduler scheduler,
            @NotNull LegaWorldManager worldManager,
            @NotNull LegaPlayerManager playerManager,
            @NotNull LegaNetworkManager networkManager,
            @NotNull LegaServerInfo serverInfo,
            @NotNull Logger logger
    ) {
        this.eventBus = eventBus;
        this.scheduler = scheduler;
        this.worldManager = worldManager;
        this.playerManager = playerManager;
        this.networkManager = networkManager;
        this.serverInfo = serverInfo;
        this.logger = logger;
    }

    /**
     * Gets the singleton instance of the LEGA API.
     *
     * @return the LEGA API instance
     * @throws IllegalStateException if LEGA has not been initialized
     */
    @NotNull
    public static LegaAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LEGA has not been initialized yet!");
        }
        return instance;
    }

    /**
     * Checks whether LEGA has been initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Internal: sets the API instance (called by the server bootstrap).
     *
     * @param api the API instance
     */
    public static void setInstance(@NotNull LegaAPI api) {
        if (instance != null) {
            throw new IllegalStateException("LEGA API instance already set!");
        }
        instance = api;
    }

    /**
     * Internal: clears the API instance (called on server shutdown).
     */
    public static void clearInstance() {
        instance = null;
    }

    // =========================================================================
    // API Accessors
    // =========================================================================

    /**
     * Gets the LEGA high-performance event bus.
     * Up to 10x faster than Bukkit's event system.
     *
     * @return the event bus
     */
    @NotNull
    public LegaEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Gets the LEGA async-capable scheduler.
     *
     * @return the scheduler
     */
    @NotNull
    public LegaScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Gets the LEGA world manager for instanced worlds.
     *
     * @return the world manager
     */
    @NotNull
    public LegaWorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Gets the player manager for connected player lookup and broadcasts.
     *
     * @return the player manager
     */
    @NotNull
    public LegaPlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Gets the network manager for raw protocol access and per-connection diagnostics.
     *
     * @return the network manager
     */
    @NotNull
    public LegaNetworkManager getNetworkManager() {
        return networkManager;
    }

    /**
     * Gets server information and runtime statistics.
     *
     * @return server info
     */
    @NotNull
    public LegaServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * Gets the LEGA logger.
     *
     * @return the logger
     */
    @NotNull
    public Logger getLogger() {
        return logger;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private LegaEventBus eventBus;
        private LegaScheduler scheduler;
        private LegaWorldManager worldManager;
        private LegaPlayerManager playerManager;
        private LegaNetworkManager networkManager;
        private LegaServerInfo serverInfo;
        private Logger logger;

        public Builder eventBus(@NotNull LegaEventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder scheduler(@NotNull LegaScheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public Builder worldManager(@NotNull LegaWorldManager worldManager) {
            this.worldManager = worldManager;
            return this;
        }

        public Builder playerManager(@NotNull LegaPlayerManager playerManager) {
            this.playerManager = playerManager;
            return this;
        }

        public Builder networkManager(@NotNull LegaNetworkManager networkManager) {
            this.networkManager = networkManager;
            return this;
        }

        public Builder serverInfo(@NotNull LegaServerInfo serverInfo) {
            this.serverInfo = serverInfo;
            return this;
        }

        public Builder logger(@NotNull Logger logger) {
            this.logger = logger;
            return this;
        }

        public LegaAPI build() {
            if (eventBus == null || scheduler == null || worldManager == null
                    || playerManager == null || networkManager == null
                    || serverInfo == null || logger == null) {
                throw new IllegalStateException("All LEGA API components must be set before building.");
            }
            return new LegaAPI(eventBus, scheduler, worldManager,
                    playerManager, networkManager, serverInfo, logger);
        }
    }
}

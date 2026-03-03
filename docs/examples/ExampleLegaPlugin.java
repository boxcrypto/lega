package net.lega.example;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.api.LegaAPI;
import net.lega.api.event.LegaEvent;
import net.lega.api.event.LegaEventBus;
import net.lega.api.event.LegaEventHandler;
import net.lega.api.scheduler.LegaScheduler;
import net.lega.api.scheduler.TaskHandle;
import net.lega.api.world.LegaWorldInstance;
import net.lega.api.world.LegaWorldManager;

import java.time.Duration;
import java.util.logging.Logger;


public final class ExampleLegaPlugin {

    private final Logger logger = Logger.getLogger("ExampleLegaPlugin");
    private TaskHandle cleanupTask;

    
    public void onEnable() {
        LegaAPI lega = LegaAPI.getInstance();
        logger.info("ExampleLegaPlugin loading on LEGA " + lega.getServerInfo().getLegaVersion());

        // 1. Register event handlers
        registerEvents(lega.getEventBus());

        // 2. Schedule tasks
        scheduleTasks(lega.getScheduler());

        // 3. Set up world instances
        setupWorlds(lega.getWorldManager());

        // 4. Log server metrics
        logMetrics(lega);
    }

    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        logger.info("ExampleLegaPlugin disabled.");
    }

    // =========================================================================
    // Events
    // =========================================================================

    private void registerEvents(LegaEventBus eventBus) {
        // Register with lambda (fastest, recommended)
        eventBus.register(ExampleEvent.class, event -> {
            logger.info("ExampleEvent received: " + event.getMessage());
        });

        // Register with annotation-style (use registerAll)
        eventBus.registerAll(new MyListener());

        logger.info("Registered event handlers. Total: " + eventBus.handlerCount());
    }

    // Listener using @Subscribe annotation
    static class MyListener {
        @LegaEventBus.Subscribe
        public void onExample(ExampleEvent event) {
            System.out.println("MyListener got: " + event.getMessage());
        }
    }

    // Custom event example
    static class ExampleEvent extends LegaEvent {
        private final String message;
        ExampleEvent(String message) { this.message = message; }
        public String getMessage() { return message; }
    }

    // =========================================================================
    // Scheduling
    // =========================================================================

    private void scheduleTasks(LegaScheduler scheduler) {
        // Run something async immediately
        scheduler.runAsync(() -> {
            logger.info("[Async] Doing heavy work off the main thread...");
            // Simulate database query
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            logger.info("[Async] Done!");
        });

        // Schedule a sync task 5 seconds from now
        scheduler.runSyncLater(() -> {
            logger.info("[Sync] 5 seconds have passed!");
        }, Duration.ofSeconds(5));

        // Schedule a repeating async task every 30 seconds
        cleanupTask = scheduler.runAsyncTimer(() -> {
            logger.info("[Async] Periodic cleanup running...");
        }, Duration.ofSeconds(30), Duration.ofSeconds(30));

        // Use tick-based scheduling
        scheduler.runSyncLaterTicks(() -> {
            logger.info("[Tick] 100 ticks later!");
        }, 100L);

        // Use CompletableFuture for chaining async → sync
        scheduler.supplyAsync(() -> {
            // Async: fetch data
            return "data from database";
        }).thenCompose(data ->
            // Switch back to main thread to use Bukkit API
            scheduler.runOnMainThread(() -> {
                logger.info("[Main] Got data on main thread: " + data);
            })
        );
    }

    // =========================================================================
    // World Instances
    // =========================================================================

    private void setupWorlds(LegaWorldManager worlds) {
        // Register a world template
        worlds.registerTemplate("practice-arena", "templates/practice-arena")
            .thenRun(() -> logger.info("Template loaded!"))
            .exceptionally(e -> {
                logger.warning("Failed to load template: " + e.getMessage());
                return null;
            });

        // Create an instance
        worlds.createInstance("practice-arena", "arena-1").thenAccept(instance -> {
            logger.info("World instance created: " + instance.getInstanceName());
            logger.info("World UUID: " + instance.getWorldUUID());
            logger.info("Players: " + instance.getPlayerCount());

            // Later: reset the instance (nearly instant from RAM template)
            worlds.resetInstance("arena-1").thenRun(() ->
                logger.info("Arena reset!")
            );
        });
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    private void logMetrics(LegaAPI lega) {
        var info = lega.getServerInfo();

        double[] tps = info.getTPS();
        logger.info(String.format("Server TPS: %.2f / %.2f / %.2f (1m/5m/15m)", tps[0], tps[1], tps[2]));
        logger.info(String.format("MSPT: %.2fms", info.getMillisPerTick()));
        logger.info(String.format("Memory: %dMB / %dMB",
                info.getUsedMemoryBytes() / (1024*1024),
                info.getTotalMemoryBytes() / (1024*1024)));
        logger.info("Performance Score: " + info.getPerformanceScore() + "/100");
        logger.info("Velocity Bridge: " + (info.isVelocityBridgeActive() ? "Active" : "Inactive"));
        logger.info("Chunks loaded: " + info.getLoadedChunkCount());
        logger.info("Entities: " + info.getEntityCount());
    }
}

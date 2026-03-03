package net.lega.performance;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public final class EntityOptimizer {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Entity");

    private final AtomicBoolean emergencyMode = new AtomicBoolean(false);
    private final AtomicInteger activationRange = new AtomicInteger(32);
    private final AtomicLong totalEntitiesOptimized = new AtomicLong(0);
    private final AtomicLong totalEntitiesMerged = new AtomicLong(0);
    private final AtomicLong totalEntitiesDespawned = new AtomicLong(0);

    private boolean asyncPathfinding = true;
    private boolean entityMerge = true;
    private boolean predictiveDespawn = true;
    private double entityMergeRadius = 2.5;

    void initialize() {
        LOGGER.info("[LEGA/Entity] Entity optimizer initialized.");
        LOGGER.info("[LEGA/Entity]   Activation range    : {} blocks", activationRange.get());
        LOGGER.info("[LEGA/Entity]   Async pathfinding   : {}", asyncPathfinding);
        LOGGER.info("[LEGA/Entity]   Entity merge        : {} (radius: {})", entityMerge, entityMergeRadius);
        LOGGER.info("[LEGA/Entity]   Predictive despawn  : {}", predictiveDespawn);
    }

    
    public boolean shouldTickEntity(double entityDistanceToNearestPlayer, String entityType) {
        if (emergencyMode.get()) {
            // In emergency mode, only tick entities very close to players
            return entityDistanceToNearestPlayer < activationRange.get() / 2.0;
        }
        return entityDistanceToNearestPlayer < activationRange.get();
    }

    
    public boolean shouldMerge(double distance, String entityType) {
        if (!entityMerge) return false;
        return distance <= entityMergeRadius
                && (entityType.equals("ITEM") || entityType.equals("EXPERIENCE_ORB"));
    }

    
    public boolean shouldPredictiveDespawn(long entityAge, double nearestPlayerDistance, String entityType) {
        if (!predictiveDespawn) return false;
        // Non-hostile mobs far from players that have been alive a long time
        if (nearestPlayerDistance > 96 && entityAge > 12000) { // 10 minutes
            return true;
        }
        // Items on the ground for more than 5 minutes with no nearby player
        if (entityType.equals("ITEM") && nearestPlayerDistance > 64 && entityAge > 6000) {
            return true;
        }
        return false;
    }

    // =========================================================================
    // Emergency mode
    // =========================================================================

    public void setEmergencyMode(boolean active) {
        boolean prev = emergencyMode.getAndSet(active);
        if (prev != active) {
            LOGGER.info("[LEGA/Entity] Emergency mode: {}", active ? "ACTIVATED" : "DEACTIVATED");
        }
    }

    public boolean isEmergencyMode() {
        return emergencyMode.get();
    }

    // =========================================================================
    // Stats
    // =========================================================================

    public long getTotalEntitiesOptimized() { return totalEntitiesOptimized.get(); }
    public long getTotalEntitiesMerged() { return totalEntitiesMerged.get(); }
    public long getTotalEntitiesDespawned() { return totalEntitiesDespawned.get(); }

    // =========================================================================
    // Config setters
    // =========================================================================

    public void setActivationRange(int range) { activationRange.set(range); }
    public void setAsyncPathfinding(boolean val) { this.asyncPathfinding = val; }
    public void setEntityMerge(boolean val) { this.entityMerge = val; }
    public void setEntityMergeRadius(double radius) { this.entityMergeRadius = radius; }
    public void setPredictiveDespawn(boolean val) { this.predictiveDespawn = val; }
}

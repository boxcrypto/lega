package net.lega.performance;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public final class TickOptimizer {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Tick");

    private final AtomicBoolean dynamicSkipping = new AtomicBoolean(false);
    private final AtomicBoolean regionizedTick = new AtomicBoolean(false);
    private final AtomicLong skippedTicks = new AtomicLong(0);

    void initialize() {
        LOGGER.info("[LEGA/TickOpt] Tick optimizer initialized.");
    }

    
    public boolean shouldSkipNonCritical(long elapsedThisTickNs, long budgetNs) {
        if (!dynamicSkipping.get()) return false;
        // Skip if we've used more than 80% of our budget
        return elapsedThisTickNs > (budgetNs * 4 / 5);
    }

    public void enableDynamicSkipping(boolean enabled) {
        boolean prev = dynamicSkipping.getAndSet(enabled);
        if (prev != enabled) {
            LOGGER.info("[LEGA/TickOpt] Dynamic tick skipping: {}", enabled ? "ON" : "OFF");
        }
    }

    public void enableRegionizedTick(boolean enabled) {
        boolean prev = regionizedTick.getAndSet(enabled);
        if (prev != enabled) {
            LOGGER.info("[LEGA/TickOpt] Regionized tick engine: {}", enabled ? "ON" : "OFF");
        }
    }

    public boolean isDynamicSkippingEnabled() { return dynamicSkipping.get(); }
    public boolean isRegionizedTickEnabled()   { return regionizedTick.get(); }
    public long getSkippedTicks()              { return skippedTicks.get(); }
}

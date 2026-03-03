package net.lega.api.server;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;

import java.util.List;


public interface LegaServerInfo {

    
    @NotNull
    String getLegaVersion();

    
    @NotNull
    String getMinecraftVersion();

    /** Returns the 1-minute, 5-minute and 15-minute TPS averages. */
    @NotNull
    double[] getTPS();

    /** Returns a list of detected performance bottleneck category names. */
    @NotNull
    List<String> getDetectedBottlenecks();

    /** Returns average milliseconds per tick over the last 100 ticks. */
    double getAverageMSPT();

    /** Returns current online player count. */
    int getOnlinePlayerCount();
}
    
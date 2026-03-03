package net.lega.api.player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Central registry and factory for {@link LegaPlayer} instances.
 *
 * <p>Obtain an instance via {@code LegaAPI.getInstance().getPlayerManager()}.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public interface LegaPlayerManager {

    // ── lookup ────────────────────────────────────────────────────────────

    /**
     * Returns the online player with exactly the given name (case-insensitive),
     * or empty if not connected.
     */
    Optional<LegaPlayer> getPlayer(String name);

    /**
     * Returns the online player with the given UUID, or empty if not connected.
     */
    Optional<LegaPlayer> getPlayer(UUID uuid);

    /** Returns an unmodifiable snapshot of all currently connected players. */
    Collection<LegaPlayer> getOnlinePlayers();

    /** Returns the current online player count. */
    int getOnlinePlayerCount();

    /** Returns the configured maximum player count. */
    int getMaxPlayers();

    // ── broadcast ─────────────────────────────────────────────────────────

    /**
     * Sends a message to all online players.
     *
     * @param message plain text or JSON component
     */
    void broadcastMessage(String message);

    /**
     * Sends a message to all online players who have the given permission.
     */
    void broadcastMessageTo(String message, String permission);

    /**
     * Sends an action bar message to all online players.
     */
    void broadcastActionBar(String message);

    /**
     * Sends a title to all online players.
     */
    void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    // ── statistics ────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of connection statistics.
     */
    ConnectionStats getConnectionStats();

    /**
     * Returns the total number of unique players that have ever joined
     * (persisted across restarts).
     */
    CompletableFuture<Long> getTotalUniquePlayerCount();

    // ── nested records ────────────────────────────────────────────────────

    /**
     * Snapshot of recent connection metrics.
     *
     * @param joinsPerMinute   rolling 1-minute join rate
     * @param quitsPerMinute   rolling 1-minute quit rate
     * @param peakOnlineToday  highest concurrent player count today
     * @param averageSessionMs average play session duration in milliseconds
     */
    record ConnectionStats(
        double joinsPerMinute,
        double quitsPerMinute,
        int    peakOnlineToday,
        long   averageSessionMs
    ) {}
}

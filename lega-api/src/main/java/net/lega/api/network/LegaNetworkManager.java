package net.lega.api.network;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level network manager providing direct protocol access and
 * per-connection diagnostics.
 *
 * <p>This is an advanced API — most plugins do not need it.
 * Use {@link net.lega.api.player.LegaPlayer} for higher-level messaging.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public interface LegaNetworkManager {

    // ── connection management ─────────────────────────────────────────────

    /**
     * Returns the currently detected protocol version for the connection
     * associated with {@code playerUuid}, or -1 if not found.
     */
    int getProtocolVersion(UUID playerUuid);

    /**
     * Returns the best-matched version adapter class name for the given
     * player's negotiated protocol. Implementation code in lega-protocol
     * can cast the returned {@code Object} to the concrete adapter type.
     */
    Object getAdapter(UUID playerUuid);

    /**
     * Returns the negotiated Minecraft version string (e.g. {@code "1.21.4"})
     * for a player, or {@code "1.7.10"} as a safe fallback.
     */
    String getMinecraftVersion(UUID playerUuid);

    // ── raw packet sending ────────────────────────────────────────────────

    /**
     * Sends a raw packet payload to a specific player.
     *
     * <p><b>Warning:</b> Malformed payloads will cause client disconnection.
     * Always use version-specific {@link VersionAdapter} methods to build payloads.</p>
     *
     * @param playerUuid target player UUID
     * @param payload    raw packet bytes (including the packet-ID VarInt prefix)
     * @return future completing when the packet is flushed to the OS socket
     */
    CompletableFuture<Void> sendRawPacket(UUID playerUuid, byte[] payload);

    /**
     * Sends the same raw packet to all online players at once using Netty's
     * group write (more efficient than individual sends).
     */
    CompletableFuture<Void> broadcastRawPacket(byte[] payload);

    /**
     * Sends a raw packet to all players whose protocol version is at least
     * {@code minProtocolId} (inclusive).
     *
     * @param payload         raw packet bytes
     * @param minProtocolId   minimum protocol version number (e.g. 47 for 1.8)
     */
    CompletableFuture<Void> broadcastRawPacketToVersion(byte[] payload, int minProtocolId);

    // ── diagnostics ───────────────────────────────────────────────────────

    /**
     * Returns the current inbound byte rate for a player's connection
     * in bytes per second (rolling 5-second average).
     */
    long getInboundBytesPerSecond(UUID playerUuid);

    /**
     * Returns the current outbound byte rate for a player's connection.
     */
    long getOutboundBytesPerSecond(UUID playerUuid);

    /**
     * Returns the total bytes received from this player since login.
     */
    long getTotalBytesReceived(UUID playerUuid);

    /**
     * Returns the total bytes sent to this player since login.
     */
    long getTotalBytesSent(UUID playerUuid);

    /**
     * Returns the number of packets dropped for a player due to rate limiting
     * or packet firewall rejection.
     */
    long getDroppedPacketCount(UUID playerUuid);

    /**
     * Returns the remote socket address for a given player.
     */
    InetSocketAddress getRemoteAddress(UUID playerUuid);

    /**
     * Returns global network statistics across all connections.
     */
    NetworkStats getGlobalStats();

    // ── IP management ─────────────────────────────────────────────────────

    /**
     * Temporarily throttles inbound packet processing for an IP address.
     *
     * @param ip           dotted-decimal IPv4 or IPv6 address
     * @param durationMs   throttle duration in milliseconds
     */
    void throttleIp(String ip, long durationMs);

    /**
     * Fully blocks an IP address from making new connections.
     *
     * @param ip     the IP to block
     * @param reason human-readable reason (logged)
     */
    void blockIp(String ip, String reason);

    /**
     * Removes an IP block.
     */
    void unblockIp(String ip);

    // ── nested record ─────────────────────────────────────────────────────

    /**
     * Server-wide network counters.
     *
     * @param totalConnections    total connections ever accepted
     * @param activeConnections   current open connections
     * @param rejectedConnections connections rejected by firewall/rate-limiter
     * @param inboundBytesTotal   total bytes received since start
     * @param outboundBytesTotal  total bytes sent since start
     */
    record NetworkStats(
        long totalConnections,
        int  activeConnections,
        long rejectedConnections,
        long inboundBytesTotal,
        long outboundBytesTotal
    ) {}
}

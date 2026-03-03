package net.lega.bungeecord.proxy;

/**
 * Immutable forwarding data extracted from the BungeeCord-injected handshake.
 *
 * <p>The BungeeCord proxy encodes forwarded player information in the
 * {@code serverAddress} field of the Minecraft Handshake packet using
 * NUL ({@code \x00}) as a delimiter:</p>
 * <pre>
 *  [original hostname]\x00[real IP]\x00[UUID string]\x00[skin properties JSON]\x00[bungeeGuardToken?]
 * </pre>
 *
 * @param realIp            The real IP address (and optional port) of the player.
 * @param playerId          The player's UUID as forwarded by the proxy.
 * @param propertiesJson    Raw JSON array string of skin texture properties.
 * @param bungeeGuardToken  BungeeGuard authentication token, or {@code null} if absent.
 * @param platform          The detected proxy platform.
 *
 * @author maatsuh
 * @since  1.0.0
 */

import java.util.UUID;

public record PlayerForwardingData(
        String realIp,
        UUID playerId,
        String propertiesJson,
        String bungeeGuardToken,
        ProxyPlatform platform
) {
    /**
     * Returns {@code true} if a BungeeGuard token was included in the handshake
     * and must be verified before the connection proceeds.
     */
    public boolean hasBungeeGuardToken() {
        return bungeeGuardToken != null && !bungeeGuardToken.isBlank();
    }
}

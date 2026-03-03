package net.lega.bungeecord.velocity;

/**
 * Immutable snapshot of a player's real identity as forwarded by a Velocity
 * proxy using <em>modern forwarding</em> (protocol version 4).
 *
 * <p>This record is populated inside {@link VelocityForwardingHandler} after
 * the Login Plugin Request/Response exchange succeeds and the HMAC-SHA256
 * signature has been verified.  It is stored on the Netty channel via
 * {@link VelocityForwardingHandler#PLAYER_INFO_KEY} so that subsequent
 * pipeline handlers can access it without re-reading the channel attribute
 * from scratch.</p>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code realIp}       – the player's actual IP address (NOT the proxy IP)</li>
 *   <li>{@code playerId}     – Mojang UUID, online-mode authenticated by Velocity</li>
 *   <li>{@code playerName}   – display name as received from Velocity</li>
 *   <li>{@code propertiesJson} – JSON-serialised skin / cape properties array,
 *       forwarded verbatim from Mojang's profile response</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import java.util.UUID;

public record VelocityPlayerInfo(
        /** The player's real remote address, e.g. {@code "192.168.1.42:12345"}. */
        String realIp,

        /** Mojang UUID verified by Velocity's online-mode authentication. */
        UUID playerId,

        /** In-game name, max 16 characters. */
        String playerName,

        /**
         * JSON array of skin/cape texture properties forwarded from Mojang's session
         * server. May be an empty JSON array ({@code "[]"}) for offline-mode players.
         * Format: {@code [{"name":"textures","value":"<base64>","signature":"<sig>"}]}
         */
        String propertiesJson
) {}

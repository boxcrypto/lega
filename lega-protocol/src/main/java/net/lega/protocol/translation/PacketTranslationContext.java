package net.lega.protocol.translation;

/**
 * Holds per-connection context used by all {@link PacketTranslator} instances
 * while they rewrite packets between the client's version and the server's
 * internal default version.
 *
 * @param clientProtocol  Raw protocol number advertised by the client handshake.
 * @param clientAdapter   Version adapter matching the client's protocol.
 * @param serverAdapter   Version adapter for the server's internal protocol.
 * @param direction       Which direction the packet under translation is
 *                        travelling ({@link TranslationDirection}).
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.VersionAdapter;

public record PacketTranslationContext(
        int             clientProtocol,
        MinecraftVersion clientVersion,
        VersionAdapter  clientAdapter,
        MinecraftVersion serverVersion,
        VersionAdapter  serverAdapter,
        TranslationDirection direction
) {}

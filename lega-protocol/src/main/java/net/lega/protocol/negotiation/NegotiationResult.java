package net.lega.protocol.negotiation;

/**
 * Immutable result of the handshake-phase version negotiation.
 *
 * <p>Attached to the Netty channel via {@link #ATTR_KEY} so that every
 * downstream handler can read the negotiated version without re-parsing
 * the handshake.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.util.AttributeKey;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.VersionAdapter;

public record NegotiationResult(
        /** Raw protocol number sent by the client. */
        int rawProtocolId,
        /** The LEGA-resolved enum constant (may be a nearest-older fallback). */
        MinecraftVersion clientVersion,
        /** The version adapter best suited for this client. */
        VersionAdapter clientAdapter,
        /** The server's default/internal version. */
        MinecraftVersion serverVersion,
        /** The server's default adapter. */
        VersionAdapter serverAdapter,
        /** {@code true} when client and server differ and translation is needed. */
        boolean translationRequired,
        /** {@code 1} = status ping, {@code 2} = login. */
        int nextState
) {
    /** Channel attribute key used to propagate the result to later handlers. */
    public static final AttributeKey<NegotiationResult> ATTR_KEY =
            AttributeKey.valueOf("lega.negotiation");
}

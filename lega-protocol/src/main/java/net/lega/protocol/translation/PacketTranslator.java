package net.lega.protocol.translation;

/**
 * Contract for a single packet translator.
 *
 * <p>Implementations rewrite one specific logical packet (identified by the
 * packet ID in the <em>source</em> version) from source format to target
 * format.  The translator is invoked once per matching packet; it may
 * modify the buffer in-place or return a <em>new</em> buffer.</p>
 *
 * <p>Implementations must be <b>stateless</b> with respect to packet content
 * — per-connection state belongs in the Netty channel attributes.
 * Implementations <em>may</em> cache pre-computed data (e.g. block ID
 * mappings) as final fields.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.buffer.ByteBuf;

public interface PacketTranslator {

    /**
     * Returns the packet ID this translator handles in the <em>source</em>
     * version (i.e. the client's version for CLIENTBOUND packets, or the
     * server's version for SERVERBOUND packets).
     */
    int sourcePacketId(PacketTranslationContext ctx);

    /**
     * Returns the direction this translator applies to.
     */
    TranslationDirection direction();

    /**
     * Rewrite {@code in} and return the translated bytes.
     *
     * <p>The caller guarantees that {@code in} starts at the payload
     * (after the packet-ID VarInt has been consumed).  Return value
     * ownership transfers to the pipeline; the pipeline is responsible
     * for releasing {@code in} if a new buffer is returned.</p>
     *
     * @param in      raw payload bytes from the source (caller-released)
     * @param ctx     per-connection translation context
     * @return        translated payload; may be {@code in} itself if no
     *                rewrite is needed, or a freshly allocated buffer
     */
    ByteBuf translate(ByteBuf in, PacketTranslationContext ctx);

    /**
     * Returns {@code true} when this translator applies to the given context.
     * Default implementation always returns {@code true}; override to guard
     * on e.g. version range.
     */
    default boolean appliesTo(PacketTranslationContext ctx) {
        return true;
    }
}

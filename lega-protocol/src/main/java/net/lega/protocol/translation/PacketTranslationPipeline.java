package net.lega.protocol.translation;

/**
 * Netty duplex handler that transparently rewrites packets between the
 * connecting client's Minecraft version and the server's internal (default)
 * version.
 *
 * <p>The handler sits in the Netty pipeline after the handshake decoder.
 * It reads the packet-ID VarInt from every frame, looks up applicable
 * {@link PacketTranslator} instances in {@link TranslationRegistry}, applies
 * them in order, and forwards the result to the next handler.</p>
 *
 * <p>If no translator matches a given packet it is forwarded unchanged
 * (zero-copy pass-through).</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.lega.protocol.negotiation.NegotiationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class PacketTranslationPipeline extends ChannelDuplexHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PacketTranslationPipeline.class);

    private final NegotiationResult negotiation;
    private final TranslationRegistry registry = TranslationRegistry.getInstance();

    public PacketTranslationPipeline(NegotiationResult negotiation) {
        this.negotiation = negotiation;
    }

    // ── CLIENTBOUND (S→C) ─────────────────────────────────────────────────

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.write(msg, promise);
            return;
        }

        PacketTranslationContext transCtx = buildContext(TranslationDirection.CLIENTBOUND);
        ByteBuf translated = translateFrame(buf, transCtx, ctx);
        ctx.write(translated, promise);
    }

    // ── SERVERBOUND (C→S) ─────────────────────────────────────────────────

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        PacketTranslationContext transCtx = buildContext(TranslationDirection.SERVERBOUND);
        ByteBuf translated = translateFrame(buf, transCtx, ctx);
        ctx.fireChannelRead(translated);
    }

    // ── core translation logic ─────────────────────────────────────────────

    /**
     * Reads the packet-ID VarInt from {@code frame}, finds matching translators
     * and applies them, then re-prepends the (possibly modified) packet ID.
     *
     * @param frame  full packet payload (after length prefix has been stripped)
     * @param ctx    translation context (direction, versions, adapters)
     * @param nCtx   Netty channel context (for buffer allocation)
     * @return       translated frame; may be the original if no translators applied
     */
    private ByteBuf translateFrame(ByteBuf frame,
                                   PacketTranslationContext ctx,
                                   ChannelHandlerContext nCtx) {
        if (!frame.isReadable()) return frame;

        frame.markReaderIndex();

        // Read the packet-ID VarInt from the frame
        int packetId = readVarInt(frame);
        if (packetId < 0) {
            frame.resetReaderIndex();
            return frame; // incomplete frame – pass through
        }
        // Determine the OUTGOING packet ID (may differ from source)
        int targetPacketId = remapPacketId(packetId, ctx);

        // Payload starts here
        int payloadStart = frame.readerIndex();
        int payloadLen   = frame.readableBytes();

        // Find applicable translators
        List<PacketTranslator> matches = registry.find(packetId, ctx);

        if (matches.isEmpty()) {
            // Zero-copy pass-through: reset and forward the whole frame
            frame.resetReaderIndex();
            return frame;
        }

        // Extract the payload slice (content after packet ID)
        ByteBuf payload = frame.retainedSlice(payloadStart, payloadLen);

        // Apply translators in chain
        for (PacketTranslator translator : matches) {
            ByteBuf next;
            try {
                next = translator.translate(payload, ctx);
            } catch (Exception e) {
                LOG.warn("Translator {} threw on packet 0x{} — passing through original",
                        translator.getClass().getSimpleName(),
                        Integer.toHexString(packetId), e);
                payload.release();
                frame.resetReaderIndex();
                return frame;
            }
            if (next != payload) {
                payload.release();
                payload = next;
            }
        }

        // Re-assemble: [target-packet-ID VarInt] + [translated payload]
        int idWidth = varIntByteCount(targetPacketId);
        ByteBuf out = nCtx.alloc().buffer(idWidth + payload.readableBytes());
        writeVarInt(out, targetPacketId);
        out.writeBytes(payload);
        payload.release();
        frame.release();
        return out;
    }

    /**
     * Maps the source packet ID to the equivalent packet ID in the target
     * version's protocol.  Delegates to the adapter's packet-ID methods.
     *
     * <p>For packets not covered by the adapter API (rare/custom packets) the
     * same ID is used — this handles unknown packets gracefully.</p>
     */
    private int remapPacketId(int sourceId, PacketTranslationContext ctx) {
        if (ctx.direction() == TranslationDirection.CLIENTBOUND) {
            // Server is emitting a packet in serverVersion format; client expects clientVersion
            return resolveClientboundId(sourceId, ctx);
        } else {
            // Client sent a packet in clientVersion format; server expects serverVersion
            return resolveServerboundId(sourceId, ctx);
        }
    }

    private int resolveClientboundId(int serverId, PacketTranslationContext ctx) {
        // Fast-path: IDs are the same between the two versions
        if (ctx.clientAdapter().packetIdChatMessage() == ctx.serverAdapter().packetIdChatMessage()
                && serverId == ctx.serverAdapter().packetIdChatMessage()) {
            return ctx.clientAdapter().packetIdChatMessage();
        }
        if (serverId == ctx.serverAdapter().packetIdChunkData())      return ctx.clientAdapter().packetIdChunkData();
        if (serverId == ctx.serverAdapter().packetIdEntityPosition()) return ctx.clientAdapter().packetIdEntityPosition();
        if (serverId == ctx.serverAdapter().packetIdTabListHeaderFooter()) return ctx.clientAdapter().packetIdTabListHeaderFooter();
        if (serverId == ctx.serverAdapter().packetIdBossBar())        return ctx.clientAdapter().packetIdBossBar();
        if (serverId == ctx.serverAdapter().packetIdTitle())          return ctx.clientAdapter().packetIdTitle();
        if (serverId == ctx.serverAdapter().packetIdActionBar())      return ctx.clientAdapter().packetIdActionBar();
        if (serverId == ctx.serverAdapter().packetIdDisconnect())     return ctx.clientAdapter().packetIdDisconnect();
        if (serverId == ctx.serverAdapter().packetIdSpawnEntity())    return ctx.clientAdapter().packetIdSpawnEntity();
        if (serverId == ctx.serverAdapter().packetIdEntityMetadata()) return ctx.clientAdapter().packetIdEntityMetadata();
        // Unknown — keep the same ID
        return serverId;
    }

    private int resolveServerboundId(int clientId, PacketTranslationContext ctx) {
        if (clientId == ctx.clientAdapter().packetIdServerboundChat())     return ctx.serverAdapter().packetIdServerboundChat();
        if (clientId == ctx.clientAdapter().packetIdServerboundPosition()) return ctx.serverAdapter().packetIdServerboundPosition();
        return clientId;
    }

    // ── context factories ─────────────────────────────────────────────────

    private PacketTranslationContext buildContext(TranslationDirection dir) {
        return new PacketTranslationContext(
                negotiation.rawProtocolId(),
                negotiation.clientVersion(),
                negotiation.clientAdapter(),
                negotiation.serverVersion(),
                negotiation.serverAdapter(),
                dir
        );
    }

    // ── VarInt utilities ──────────────────────────────────────────────────

    static int readVarInt(ByteBuf buf) {
        int result = 0, shift = 0;
        while (buf.isReadable()) {
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift >= 35) throw new IllegalArgumentException("VarInt too large");
        }
        return -1;
    }

    static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    static int varIntByteCount(int value) {
        int n = 0;
        do { value >>>= 7; n++; } while (value != 0);
        return n;
    }
}

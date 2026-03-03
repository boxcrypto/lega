package net.lega.protocol.handler;

/**
 * Netty {@link ByteToMessageDecoder} that reads the Minecraft Handshake packet
 * (0x00 in the Handshaking state) from the raw TCP stream, performs protocol
 * version negotiation, and stores a {@link NegotiationResult} on the channel.
 *
 * <p>Minecraft Handshake wire format (all fields VarInt-prefixed):</p>
 * <pre>
 *  Field              Type        Notes
 *  ─────────────────────────────────────────────────────────────
 *  Packet length      VarInt      Total bytes that follow
 *  Packet ID          VarInt      Always 0x00
 *  Protocol version   VarInt      e.g. 47 for 1.8, 765 for 1.20.4
 *  Server address     String      May contain BungeeCord NUL-delimited forwards
 *  Server port        UInt16      Big-endian unsigned short
 *  Next state         VarInt      1 = Status, 2 = Login
 * </pre>
 *
 * <p>After extraction the decoder removes itself from the pipeline and
 * forwards the <em>full original bytes</em> so subsequent decoders (login,
 * status) can handle the packet normally.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.VersionAdapter;
import net.lega.protocol.negotiation.NegotiationResult;
import net.lega.protocol.negotiation.VersionNegotiator;
import net.lega.protocol.translation.PacketTranslationPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class HandshakePacketDecoder extends ByteToMessageDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(HandshakePacketDecoder.class);

    /** Maximum length of a Minecraft handshake packet (generous upper bound). */
    private static final int MAX_HANDSHAKE_LENGTH = 512;

    private final VersionNegotiator negotiator;

    public HandshakePacketDecoder(VersionNegotiator negotiator) {
        this.negotiator = negotiator;
        setSingleDecode(true); // only the first packet is a handshake
    }

    // ── decode ─────────────────────────────────────────────────────────────────

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Mark so we can reset if not enough data yet
        in.markReaderIndex();

        // Read packet length VarInt
        int packetLength = readVarInt(in);
        if (packetLength < 0) {
            in.resetReaderIndex();
            return; // not enough bytes yet
        }
        if (packetLength > MAX_HANDSHAKE_LENGTH) {
            LOG.warn("[{}] Oversized handshake packet ({} bytes) — closing",
                    ctx.channel().remoteAddress(), packetLength);
            ctx.close();
            return;
        }
        if (in.readableBytes() < packetLength) {
            in.resetReaderIndex();
            return; // wait for full packet
        }

        // Snapshot the full packet bytes BEFORE we read further,
        // so we can re-emit them verbatim.
        int startIndex = in.readerIndex();

        // Packet ID — must be 0x00
        int packetId = readVarInt(in);
        if (packetId != 0x00) {
            LOG.warn("[{}] Expected handshake (0x00), got 0x{} — closing",
                    ctx.channel().remoteAddress(), Integer.toHexString(packetId));
            ctx.close();
            return;
        }

        // Protocol version
        int protocolVersion = readVarInt(in);

        // Server address (skip — may contain BungeeCord data handled separately)
        readString(in);

        // Server port (unsigned short)
        in.readUnsignedShort();

        // Next state: 1 = status, 2 = login
        int nextState = readVarInt(in);

        // ── negotiation ────────────────────────────────────────────────────────
        if (nextState == 2 && !negotiator.isAllowed(protocolVersion)) {
            LOG.info("[{}] Protocol {} not in enabled list — disconnecting",
                    ctx.channel().remoteAddress(), protocolVersion);
            sendDisconnect(ctx, protocolVersion,
                    "Your version (" + protocolVersion + ") is not supported.\n"
                    + "Supported: " + negotiator.enabledVersionRange());
            return;
        }

        MinecraftVersion clientVersion   = negotiator.resolve(protocolVersion);
        VersionAdapter   clientAdapter   = negotiator.adapterFor(clientVersion);
        MinecraftVersion serverVersion   = negotiator.getDefaultVersion();
        VersionAdapter   serverAdapter   = negotiator.adapterFor(serverVersion);
        boolean          requiresTranslation = clientVersion != serverVersion;

        NegotiationResult result = new NegotiationResult(
                protocolVersion, clientVersion, clientAdapter,
                serverVersion, serverAdapter, requiresTranslation,
                nextState);

        ctx.channel().attr(NegotiationResult.ATTR_KEY).set(result);

        if (requiresTranslation) {
            LOG.debug("[{}] Version mismatch: client={} server={} — translation ON",
                    ctx.channel().remoteAddress(),
                    clientVersion.getDisplayName(),
                    serverVersion.getDisplayName());
            // Inject a translation pipeline AFTER this decoder
            ctx.pipeline().addAfter(
                    ctx.name(),
                    "lega-translation",
                    new PacketTranslationPipeline(result));
        }

        // Re-emit the original bytes so subsequent handlers see the full handshake
        int endIndex = in.readerIndex();
        in.readerIndex(startIndex - varIntByteCount(packetLength)); // go back to the length prefix
        ByteBuf copy = in.readRetainedSlice(endIndex - (startIndex - varIntByteCount(packetLength)));
        out.add(copy);

        // Remove ourselves; the next packet is Login 0x00 or Status
        ctx.pipeline().remove(this);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Sends a disconnect packet (Login 0x00 Disconnect in Login state, or a
     * legacy-compatible kick) and closes the channel.
     */
    private void sendDisconnect(ChannelHandlerContext ctx, int protocolId, String reason) {
        // Encode the JSON chat reason
        String json = "{\"text\":\"" + reason.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        ByteBuf buf = ctx.alloc().buffer(4 + jsonBytes.length);
        // Packet ID 0x00 (Login Disconnect)
        writeVarInt(buf, 0x00);
        // Reason string
        writeVarInt(buf, jsonBytes.length);
        buf.writeBytes(jsonBytes);

        // Length-prefixed frame
        ByteBuf frame = ctx.alloc().buffer(5 + buf.readableBytes());
        writeVarInt(frame, buf.readableBytes());
        frame.writeBytes(buf);
        buf.release();

        ctx.writeAndFlush(frame).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }

    /**
     * Reads a standard Minecraft VarInt from {@code buf}.
     *
     * @return the decoded value, or {@code -1} if there are not enough bytes
     */
    static int readVarInt(ByteBuf buf) {
        int result = 0;
        int shift  = 0;
        while (buf.isReadable()) {
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift >= 35) {
                throw new IllegalArgumentException("VarInt too large (>5 bytes)");
            }
        }
        return -1; // incomplete
    }

    /**
     * Reads a length-prefixed UTF-8 string from {@code buf}.
     *
     * @return the string, or {@code ""} if 0 bytes remain
     */
    static String readString(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length <= 0) return "";
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Writes a VarInt to {@code buf}. */
    static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    /** Returns the byte-width a VarInt value would occupy. */
    static int varIntByteCount(int value) {
        int count = 0;
        do { value >>>= 7; count++; } while (value != 0);
        return count;
    }
}

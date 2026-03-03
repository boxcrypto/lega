package net.lega.bungeecord.velocity;

/**
 * Netty pipeline handler that implements the <em>Velocity Modern Forwarding</em>
 * (v4) protocol for receiving a player's real identity from the Velocity proxy.
 *
 * <h3>Protocol overview</h3>
 * <ol>
 *   <li>Client (proxied through Velocity) connects and sends the normal Minecraft
 *       Handshake packet (0x00, nextState=2).</li>
 *   <li>Client sends {@code LoginStart} (0x00 in Login state).</li>
 *   <li>This handler intercepts {@code LoginStart}, <em>holds it</em>, and sends a
 *       {@code LoginPluginRequest} (0x04 in Login state) back to the client with
 *       channel {@code "velocity:player_info"} and a single byte payload
 *       {@code 0x04} indicating the max supported forwarding version.</li>
 *   <li>Velocity intercepts that request, builds the signed forwarding payload,
 *       and returns a {@code LoginPluginResponse} (0x02 in Login state) containing:
 *       <ul>
 *         <li>{@code version} (VarInt) – forwarding version (4)</li>
 *         <li>{@code realIp} (String)</li>
 *         <li>UUID as two raw {@code long} fields (MSB then LSB)</li>
 *         <li>{@code playerName} (String)</li>
 *         <li>skin properties array</li>
 *         <li>HMAC-SHA256 (32 bytes) of all the above, signed with the shared secret</li>
 *       </ul></li>
 *   <li>This handler verifies the HMAC, parses the payload into a
 *       {@link VelocityPlayerInfo} record, and stores it as a channel attribute
 *       under {@link #PLAYER_INFO_KEY}.</li>
 *   <li>The saved {@code LoginStart} bytes are re-emitted so that downstream
 *       handlers continue normal login processing with the verified player data.</li>
 *   <li>This handler removes itself from the pipeline.</li>
 * </ol>
 *
 * <h3>Pipeline placement</h3>
 * <p>Must be placed AFTER the frame-decoder (so frames are already stripped) but
 * BEFORE the handshake decoder.  In practice add it with
 * {@code pipeline().addFirst("velocity-forwarding", handler)}.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@io.netty.channel.ChannelHandler.Sharable
public final class VelocityForwardingHandler extends ChannelDuplexHandler {

    // ── Channel attribute ─────────────────────────────────────────────────────

    /**
     * Channel attribute key that holds the verified {@link VelocityPlayerInfo}
     * after successful HMAC verification, accessible by later pipeline handlers.
     */
    public static final AttributeKey<VelocityPlayerInfo> PLAYER_INFO_KEY =
            AttributeKey.valueOf("lega.velocity.player");

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Velocity modern forwarding channel identifier. */
    private static final String VELOCITY_CHANNEL = "velocity:player_info";

    /** Maximum modern forwarding version we support. */
    private static final int MAX_FORWARDING_VERSION = 4;

    /** HMAC-SHA256 output size in bytes. */
    private static final int HMAC_LENGTH = 32;

    /** Packet IDs for the Login connection state. */
    private static final int PKT_SERVERBOUND_LOGIN_START          = 0x00;
    private static final int PKT_SERVERBOUND_LOGIN_PLUGIN_RESPONSE = 0x02;
    private static final int PKT_CLIENTBOUND_LOGIN_PLUGIN_REQUEST  = 0x04;

    private static final Logger LOG = LoggerFactory.getLogger(VelocityForwardingHandler.class);

    // ── State ─────────────────────────────────────────────────────────────────

    private enum Phase { HANDSHAKE, LOGIN_START, AWAIT_PLUGIN_RESPONSE, DONE }

    private Phase phase = Phase.HANDSHAKE;

    /**
     * The {@code messageId} sent in our Login Plugin Request.  Velocity must
     * echo the same ID back in its Login Plugin Response.
     */
    private int sentMessageId;

    /**
     * A retained copy of the {@code LoginStart} packet bytes.  Held while we
     * wait for Velocity's Login Plugin Response, then re-emitted once verified.
     */
    private ByteBuf savedLoginStart;

    // ── Configuration ─────────────────────────────────────────────────────────

    private final byte[] secretBytes;

    /**
     * Creates a new handler instance.  One instance must be allocated per
     * channel connection; do NOT share between channels.
     *
     * @param forwardingSecret  the shared secret from {@code velocity.toml}
     *                          ({@code forwarding-secret}) and {@code proxy.yml}
     *                          ({@code velocity-support.secret})
     */
    public VelocityForwardingHandler(String forwardingSecret) {
        this.secretBytes = forwardingSecret.getBytes(StandardCharsets.UTF_8);
    }

    // ── Inbound ───────────────────────────────────────────────────────────────

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.fireChannelRead(msg);
            return;
        }
        switch (phase) {
            case HANDSHAKE            -> handleHandshake(ctx, buf);
            case LOGIN_START          -> handleLoginStart(ctx, buf);
            case AWAIT_PLUGIN_RESPONSE -> handlePluginResponse(ctx, buf);
            default                   -> ctx.fireChannelRead(buf);  // DONE: pass through
        }
    }

    /** Pass the Handshake packet untouched; advance phase to LOGIN_START. */
    private void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        // Peek at packet ID VarInt (expect 0x00 = Handshake)
        buf.markReaderIndex();
        int packetId = readVarInt(buf);
        buf.resetReaderIndex();

        if (packetId == 0x00) {
            phase = Phase.LOGIN_START;
        }
        ctx.fireChannelRead(buf.retain());
        buf.release();
    }

    /**
     * Called when the first Login-state packet arrives.
     * For Velocity this should be a {@code LoginStart} (0x00).
     * We save the bytes and send a {@code LoginPluginRequest}.
     */
    private void handleLoginStart(ChannelHandlerContext ctx, ByteBuf buf) {
        buf.markReaderIndex();
        int packetId = readVarInt(buf);
        buf.resetReaderIndex();

        if (packetId != PKT_SERVERBOUND_LOGIN_START) {
            // Not a LoginStart — pass through and stay in this phase
            ctx.fireChannelRead(buf.retain());
            buf.release();
            return;
        }

        // Save for re-emission after verification
        savedLoginStart = buf.retain();
        phase = Phase.AWAIT_PLUGIN_RESPONSE;

        // Generate a unique message ID (use a simple counter approach; non-zero)
        sentMessageId = (int) (System.nanoTime() & 0x7F_FFFF_FFL) + 1;

        LOG.debug("[Velocity] LoginStart intercepted; sending Login Plugin Request msgId={}", sentMessageId);
        writeLoginPluginRequest(ctx);
    }

    /**
     * Called when a packet arrives while we are waiting for Velocity's
     * {@code LoginPluginResponse}.
     */
    private void handlePluginResponse(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        buf.markReaderIndex();
        int packetId = readVarInt(buf);

        if (packetId != PKT_SERVERBOUND_LOGIN_PLUGIN_RESPONSE) {
            // A non-response packet arrived first — pass through, keep waiting
            buf.resetReaderIndex();
            ctx.fireChannelRead(buf.retain());
            buf.release();
            return;
        }

        try {
            int responseMessageId = readVarInt(buf);
            if (responseMessageId != sentMessageId) {
                LOG.warn("[Velocity] Plugin response messageId mismatch: expected={} got={} — dropping",
                        sentMessageId, responseMessageId);
                disconnectClient(ctx, "Velocity forwarding protocol error (messageId mismatch).");
                buf.release();
                return;
            }

            boolean success = buf.readBoolean();
            if (!success) {
                LOG.warn("[Velocity] Proxy returned success=false on Login Plugin Response — player not forwarded.");
                disconnectClient(ctx, "This server requires a Velocity proxy (velocity:player_info channel rejected).");
                buf.release();
                return;
            }

            // Remaining bytes = payload + HMAC
            int dataLen = buf.readableBytes();
            if (dataLen < HMAC_LENGTH) {
                LOG.error("[Velocity] Plugin response payload too short ({} bytes); expected at least {} (HMAC only)",
                        dataLen, HMAC_LENGTH);
                disconnectClient(ctx, "Velocity forwarding data invalid (payload too short).");
                buf.release();
                return;
            }

            byte[] payload   = new byte[dataLen - HMAC_LENGTH];
            byte[] signature = new byte[HMAC_LENGTH];
            buf.readBytes(payload);
            buf.readBytes(signature);
            buf.release();

            // Verify HMAC-SHA256
            byte[] expected = computeHmac(secretBytes, payload);
            if (!MessageDigest.isEqual(expected, signature)) {
                LOG.error("[Velocity] HMAC verification FAILED — possible spoofing or wrong secret.");
                disconnectClient(ctx, "Velocity forwarding HMAC verification failed.");
                return;
            }

            // Parse payload
            VelocityPlayerInfo info = parseForwardingPayload(payload, extractRemoteAddress(ctx));
            ctx.channel().attr(PLAYER_INFO_KEY).set(info);
            LOG.info("[Velocity] Verified player UUID={} name='{}' realIp={}",
                    info.playerId(), info.playerName(), info.realIp());

            // Done — remove self, re-emit saved LoginStart
            phase = Phase.DONE;
            ctx.pipeline().remove(this);

            ByteBuf loginStart = savedLoginStart;
            savedLoginStart = null;
            ctx.fireChannelRead(loginStart);

        } catch (Exception e) {
            LOG.error("[Velocity] Fatal error processing Login Plugin Response", e);
            disconnectClient(ctx, "Internal server error during Velocity forwarding.");
            buf.release();
        }
    }

    // ── Outbound ──────────────────────────────────────────────────────────────

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Pass all outbound writes through; we only inject the Login Plugin Request
        // explicitly via writeLoginPluginRequest() which writes directly.
        ctx.write(msg, promise);
    }

    // ── Login Plugin Request builder ─────────────────────────────────────────

    /**
     * Writes a {@code LoginPluginRequest} (0x04 in Login state) to the channel.
     * Payload: a single byte {@code 0x04} — the max forwarding version we support.
     * The pipeline's existing frame-encoder will prepend the VarInt length header.
     */
    private void writeLoginPluginRequest(ChannelHandlerContext ctx) {
        String channel = VELOCITY_CHANNEL;
        byte[] channelBytes = channel.getBytes(StandardCharsets.UTF_8);

        // Compute buffer size:
        //   packetId (VarInt 0x04 = 1 byte)
        //   messageId VarInt (up to 5 bytes)
        //   channelName length VarInt + bytes
        //   1 byte payload (max version)
        int channelNameLen = channelBytes.length;
        int capacity = 1 + varIntByteCount(sentMessageId)
                + varIntByteCount(channelNameLen) + channelNameLen + 1;

        ByteBuf pkt = ctx.alloc().buffer(capacity);
        writeVarInt(pkt, PKT_CLIENTBOUND_LOGIN_PLUGIN_REQUEST);
        writeVarInt(pkt, sentMessageId);
        writeVarInt(pkt, channelNameLen);
        pkt.writeBytes(channelBytes);
        pkt.writeByte(MAX_FORWARDING_VERSION);   // tell Velocity we support v4

        ctx.writeAndFlush(pkt);
    }

    // ── Payload parser ────────────────────────────────────────────────────────

    /**
     * Parses the Velocity modern forwarding (v4) payload into a
     * {@link VelocityPlayerInfo}.
     *
     * @param payload the HMAC-verified payload bytes
     * @param fallbackIp  fallback IP string if remote address cannot be determined
     * @return populated player info record
     * @throws IllegalArgumentException if the payload version is not supported
     */
    private static VelocityPlayerInfo parseForwardingPayload(byte[] payload, String fallbackIp) {
        ByteBuf buf = Unpooled.wrappedBuffer(payload);
        try {
            int version = readVarInt(buf);
            if (version < 1 || version > MAX_FORWARDING_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Velocity forwarding version: " + version
                        + " (server supports 1–" + MAX_FORWARDING_VERSION + ")");
            }

            String realIp   = readString(buf);
            long uuidMsb    = buf.readLong();
            long uuidLsb    = buf.readLong();
            UUID uuid       = new UUID(uuidMsb, uuidLsb);
            String name     = readString(buf);

            // Properties (skin / cape textures)
            StringBuilder jsonBuilder = new StringBuilder("[");
            if (buf.isReadable()) {
                int propCount = readVarInt(buf);
                for (int i = 0; i < propCount; i++) {
                    String propName  = readString(buf);
                    String propValue = readString(buf);
                    boolean hasSig   = buf.readBoolean();
                    String propSig   = hasSig ? readString(buf) : null;

                    if (i > 0) jsonBuilder.append(',');
                    jsonBuilder.append('{');
                    jsonBuilder.append("\"name\":\"").append(escapeJson(propName)).append('"');
                    jsonBuilder.append(",\"value\":\"").append(escapeJson(propValue)).append('"');
                    if (propSig != null) {
                        jsonBuilder.append(",\"signature\":\"").append(escapeJson(propSig)).append('"');
                    }
                    jsonBuilder.append('}');
                }
            }
            jsonBuilder.append(']');

            if (realIp == null || realIp.isBlank()) realIp = fallbackIp;

            return new VelocityPlayerInfo(realIp, uuid, name, jsonBuilder.toString());
        } finally {
            buf.release();
        }
    }

    // ── Disconnect helper ─────────────────────────────────────────────────────

    /**
     * Sends a {@code Disconnect} (Login state, clientbound 0x00) and closes
     * the channel.  This is a best-effort send; channel is closed regardless.
     */
    private void disconnectClient(ChannelHandlerContext ctx, String reason) {
        try {
            String json = "{\"text\":\"" + escapeJson(reason) + "\"}";
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            ByteBuf pkt = ctx.alloc().buffer(1 + 5 + jsonBytes.length);
            writeVarInt(pkt, 0x00); // Disconnect (Login)
            writeVarInt(pkt, jsonBytes.length);
            pkt.writeBytes(jsonBytes);
            ctx.writeAndFlush(pkt);
        } catch (Exception ignored) {
            // best effort
        } finally {
            ctx.channel().close();
            if (savedLoginStart != null) {
                savedLoginStart.release();
                savedLoginStart = null;
            }
        }
    }

    // ── Channel cleanup ───────────────────────────────────────────────────────

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (savedLoginStart != null) {
            savedLoginStart.release();
            savedLoginStart = null;
        }
        super.channelInactive(ctx);
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    /**
     * Computes HMAC-SHA256 of {@code data} using {@code key}.
     *
     * @throws RuntimeException on JCA failure (should never happen on a
     *                          standard JVM)
     */
    static byte[] computeHmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }

    // ── Channel address helper ────────────────────────────────────────────────

    private static String extractRemoteAddress(ChannelHandlerContext ctx) {
        try {
            if (ctx.channel().remoteAddress() instanceof InetSocketAddress isa) {
                return isa.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    // ── VarInt / String helpers ───────────────────────────────────────────────

    static int readVarInt(ByteBuf buf) {
        int value = 0, shift = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new RuntimeException("VarInt too long");
        } while ((b & 0x80) != 0);
        return value;
    }

    static String readString(ByteBuf buf) {
        int len = readVarInt(buf);
        if (len < 0 || len > 32767) throw new RuntimeException("String length out of range: " + len);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    static int varIntByteCount(int value) {
        int count = 1;
        while ((value & ~0x7F) != 0) { count++; value >>>= 7; }
        return count;
    }

    // ── JSON helper ───────────────────────────────────────────────────────────

    /** Minimal JSON string escaper (handles {@code "}, {@code \}, and control chars). */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

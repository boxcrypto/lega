package net.lega.bungeecord.proxy;

/**
 * Netty {@link ChannelInboundHandlerAdapter} that intercepts the raw Minecraft
 * Handshake packet and extracts the BungeeCord IP-forwarding payload injected
 * into the {@code serverAddress} field.
 *
 * <h3>How BungeeCord IP forwarding works</h3>
 * <p>When a player connects through a BungeeCord/Waterfall/Travertine proxy with
 * IP forwarding enabled, the proxy replaces the {@code serverAddress} field in the
 * Minecraft Handshake packet with a NUL-delimited string:</p>
 * <pre>
 *   {original-hostname}\x00{player-ip}\x00{player-uuid}\x00{skin-properties-json}[\x00{bungeeGuardToken}]
 * </pre>
 *
 * <p>This handler:</p>
 * <ol>
 *   <li>Peeks at the incoming Handshake packet (<em>without consuming it</em>).</li>
 *   <li>Attempts to parse BungeeCord forwarding data from the server-address field.</li>
 *   <li>If found, verifies the BungeeGuard token (when configured).</li>
 *   <li>Stores a {@link PlayerForwardingData} on the channel attribute
 *       {@link #ATTR_KEY} for downstream handlers.</li>
 *   <li>Rewrites the handshake in the buffer, replacing the extended server-address
 *       with just the original hostname so that the vanilla login logic is unaffected.</li>
 *   <li>Rejects direct connections when {@code reject-direct-connections: true}.</li>
 * </ol>
 *
 * <p>This handler inserts itself <em>before</em> the
 * {@code HandshakePacketDecoder}, so the version negotiation handler sees a
 * clean handshake regardless of whether IP forwarding was active.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class BungeeCordHandshakeInterceptor extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(BungeeCordHandshakeInterceptor.class);

    /** Channel attribute used to expose forwarding data to downstream handlers. */
    public static final AttributeKey<PlayerForwardingData> ATTR_KEY =
            AttributeKey.valueOf("lega.proxy.forwarding");

    private final BungeeCordBridgeConfig config;
    private final BungeeGuardVerifier bungeeGuardVerifier;

    public BungeeCordHandshakeInterceptor(BungeeCordBridgeConfig config) {
        this.config = config;
        this.bungeeGuardVerifier = config.isBungeeGuardEnabled()
                ? new BungeeGuardVerifier(config.getBungeeGuardTokens())
                : null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        // Work on a copy so we can peek without disrupting downstream
        frame.markReaderIndex();

        try {
            // Read packet length + ID
            int length   = readVarInt(frame);
            int packetId = readVarInt(frame);

            if (packetId != 0x00) {
                // Not a handshake packet — pass through unchanged
                frame.resetReaderIndex();
                ctx.fireChannelRead(msg);
                ctx.pipeline().remove(this);
                return;
            }

            // Read protocol version
            int protocolVersion = readVarInt(frame);

            // Read server address (possibly BungeeCord-injected)
            String serverAddress = readString(frame);
            int serverPort       = frame.readUnsignedShort();
            int nextState        = readVarInt(frame);

            // ── Try to detect BungeeCord forwarding data ───────────────────────
            if (!serverAddress.contains("\u0000")) {
                // No NUL separator — direct connection
                if (config.isRejectDirectConnections()) {
                    LOG.warn("[{}] Direct connection rejected (proxy bridge requires proxy).",
                            ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }
                // Allow direct connection without forwarding
                frame.resetReaderIndex();
                ctx.fireChannelRead(msg);
                ctx.pipeline().remove(this);
                return;
            }

            // Split the server-address field on NUL
            String[] parts = serverAddress.split("\u0000", -1);
            // parts[0] = original hostname
            // parts[1] = real player IP
            // parts[2] = UUID string
            // parts[3] = skin properties JSON
            // parts[4] = BungeeGuard token (optional)

            if (parts.length < 4) {
                LOG.warn("[{}] Malformed BungeeCord handshake (only {} parts) — closing",
                        ctx.channel().remoteAddress(), parts.length);
                ctx.close();
                return;
            }

            String realIp       = parts[1];
            String uuidString   = parts[2];
            String propertiesJson = parts[3];
            String bungeeToken  = parts.length >= 5 ? parts[4] : null;

            // ── BungeeGuard verification ───────────────────────────────────────
            if (config.isBungeeGuardEnabled()) {
                if (bungeeGuardVerifier == null
                        || !bungeeGuardVerifier.verify(bungeeToken,
                                String.valueOf(ctx.channel().remoteAddress()))) {
                    ctx.close();
                    return;
                }
            }

            // ── Parse UUID ─────────────────────────────────────────────────────
            UUID playerId;
            try {
                // BungeeCord may omit the dashes: "7c9d5b26e5c14b8f9…"
                String uuidNorm = uuidString.length() == 32
                        ? uuidString.substring(0, 8) + '-'
                          + uuidString.substring(8, 12) + '-'
                          + uuidString.substring(12, 16) + '-'
                          + uuidString.substring(16, 20) + '-'
                          + uuidString.substring(20)
                        : uuidString;
                playerId = UUID.fromString(uuidNorm);
            } catch (IllegalArgumentException e) {
                LOG.warn("[{}] Invalid UUID in BungeeCord forwarding: '{}' — closing",
                        ctx.channel().remoteAddress(), uuidString);
                ctx.close();
                return;
            }

            // ── Detect platform ────────────────────────────────────────────────
            ProxyPlatform platform = bungeeToken != null
                    ? (config.getPlatform() == ProxyPlatform.TRAVERTINE
                       ? ProxyPlatform.TRAVERTINE : ProxyPlatform.WATERFALL)
                    : ProxyPlatform.BUNGEECORD;

            PlayerForwardingData forwarding = new PlayerForwardingData(
                    realIp, playerId, propertiesJson, bungeeToken, platform);

            ctx.channel().attr(ATTR_KEY).set(forwarding);
            LOG.debug("[{}] Proxy forwarding accepted: uuid={} ip={} platform={}",
                    ctx.channel().remoteAddress(), playerId, realIp, platform.getDisplayName());

            // ── Rewrite handshake: replace serverAddress with cleaned hostname ──
            String cleanHostname = parts[0];
            ByteBuf rewritten = rebuildHandshake(
                    ctx, protocolVersion, cleanHostname, serverPort, nextState);

            frame.release();
            ctx.fireChannelRead(rewritten);

        } catch (Exception e) {
            LOG.error("[{}] Error processing BungeeCord handshake — closing",
                    ctx.channel().remoteAddress(), e);
            ctx.close();
        } finally {
            ctx.pipeline().remove(this);
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Rebuilds the Minecraft Handshake packet with a clean {@code serverAddress}
     * (original hostname only, no BungeeCord data).
     */
    private ByteBuf rebuildHandshake(ChannelHandlerContext ctx,
                                     int protocolVersion,
                                     String cleanHostname,
                                     int serverPort,
                                     int nextState) {
        ByteBuf payload = ctx.alloc().buffer(128);
        writeVarInt(payload, 0x00);                   // packet ID
        writeVarInt(payload, protocolVersion);
        writeString(payload, cleanHostname);
        payload.writeShort(serverPort);
        writeVarInt(payload, nextState);

        ByteBuf frame = ctx.alloc().buffer(5 + payload.readableBytes());
        writeVarInt(frame, payload.readableBytes());  // length prefix
        frame.writeBytes(payload);
        payload.release();
        return frame;
    }

    // ── VarInt / String I/O ────────────────────────────────────────────────────

    private static int readVarInt(ByteBuf buf) {
        int r = 0, s = 0;
        while (buf.isReadable()) {
            byte b = buf.readByte();
            r |= (b & 0x7F) << s;
            if ((b & 0x80) == 0) return r;
            s += 7;
        }
        return 0;
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static String readString(ByteBuf buf) {
        int len = readVarInt(buf);
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}

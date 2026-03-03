package net.lega.protocol.handler;

/**
 * Netty {@link ChannelInitializer} that wires the full LEGA pipeline for each
 * accepted TCP connection.
 *
 * <p>Pipeline order (client → server):</p>
 * <pre>
 *  [frame-decoder]  LengthFieldBasedFrameDecoder  (4 MB max)
 *  [handshake]      HandshakePacketDecoder         (reads version, then self-removes)
 *  [frame-encoder]  LengthFieldPrepender           (adds length prefix on write)
 *  [lega-translation]  PacketTranslationPipeline   (added dynamically after handshake if needed)
 * </pre>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.lega.protocol.negotiation.VersionNegotiator;

import java.util.concurrent.TimeUnit;

public final class VersionAwareChannelInitializer extends ChannelInitializer<SocketChannel> {

    /** Maximum raw frame size we accept from a client (4 MiB). */
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

    /**
     * VarInt-encoded packet lengths use up to 3 bytes for frames ≤ 2 097 151 bytes.
     * We use a simple approach: a 3-byte big-endian length header stripped on read.
     */
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH  = 3; // max VarInt frames in MC
    private static final int LENGTH_ADJUSTMENT    = 0;
    private static final int INITIAL_BYTES_STRIP  = 3;

    /** Seconds without data before the connection is closed. */
    private static final int READ_TIMEOUT_SECONDS = 30;

    private final VersionNegotiator negotiator;

    public VersionAwareChannelInitializer(VersionNegotiator negotiator) {
        this.negotiator = negotiator;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
          // Idle-connection timeout — boots AFK TCP connections
          .addLast("read-timeout",
                  new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))

          // Inbound: strip the 3-byte big-endian length prefix that precedes each MC packet
          .addLast("frame-decoder",
                  new LengthFieldBasedFrameDecoder(
                          MAX_FRAME_BYTES,
                          LENGTH_FIELD_OFFSET,
                          LENGTH_FIELD_LENGTH,
                          LENGTH_ADJUSTMENT,
                          INITIAL_BYTES_STRIP))

          // Outbound: prepend the 3-byte length header before writing each packet
          .addLast("frame-encoder",
                  new LengthFieldPrepender(LENGTH_FIELD_LENGTH))

          // Decodes the first packet (Handshake), negotiates version,
          // optionally adds PacketTranslationPipeline, then removes itself
          .addLast("handshake",
                  new HandshakePacketDecoder(negotiator));
    }
}

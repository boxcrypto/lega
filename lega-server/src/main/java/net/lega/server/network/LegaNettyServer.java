package net.lega.server.network;

/**
 * LEGA's Netty-based TCP server that accepts Minecraft client connections.
 *
 * <p>Uses {@link VersionAwareChannelInitializer} to handle multi-version
 * clients natively, and optionally prepends the
 * {@link LegaBungeeCordBridge} interceptor for proxy-forwarded connections.</p>
 *
 * <h3>Threading model</h3>
 * <ul>
 *   <li>{@code boss} group: 1 thread — accepts incoming TCP connections</li>
 *   <li>{@code worker} group: {@code 2 × CPU} threads — handles I/O</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.lega.bungeecord.LegaBungeeCordBridge;
import net.lega.protocol.negotiation.VersionNegotiator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public final class LegaNettyServer {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Network");

    private final int port;
    private final VersionNegotiator negotiator;
    private final LegaBungeeCordBridge bridge;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;

    public LegaNettyServer(int port,
                            VersionNegotiator negotiator,
                            LegaBungeeCordBridge bridge) {
        this.port       = port;
        this.negotiator = negotiator;
        this.bridge     = bridge;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Binds the server to the configured port and begins accepting connections.
     *
     * @throws Exception if the bind fails (e.g. port already in use)
     */
    public void start() throws Exception {
        int cpus = Runtime.getRuntime().availableProcessors();
        bossGroup   = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(cpus * 2);

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                // SO_BACKLOG: max pending connections in the OS accept queue
                .option(ChannelOption.SO_BACKLOG, 512)
                // Reuse address so the port is available immediately after restart
                .option(ChannelOption.SO_REUSEADDR, true)
                // TCP keep-alive; disconnects dead connections at the OS level
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // Disable Nagle's algorithm for lower latency per-packet
                .childOption(ChannelOption.TCP_NODELAY, true)
                // I/O buffer sizes tuned for Minecraft
                .childOption(ChannelOption.SO_RCVBUF, 65536)
                .childOption(ChannelOption.SO_SNDBUF, 65536)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 1. BungeeCord/Waterfall/Travertine interceptor — raw stream, before framing
                        if (bridge.isEnabled()
                                && !bridge.getConfig().getPlatform().usesModernForwarding()) {
                            ch.pipeline().addLast(
                                    "proxy-interceptor",
                                    bridge.createInterceptor());
                        }
                        // 2. Idle-connection read timeout
                        ch.pipeline().addLast("read-timeout",
                                new ReadTimeoutHandler(30, TimeUnit.SECONDS));
                        // 3. Frame decoder (strips 3-byte big-endian length prefix)
                        ch.pipeline().addLast("frame-decoder",
                                new io.netty.handler.codec.LengthFieldBasedFrameDecoder(
                                        4 * 1024 * 1024, 0, 3, 0, 3));
                        // 4. Frame encoder (prepends 3-byte length header)
                        ch.pipeline().addLast("frame-encoder",
                                new io.netty.handler.codec.LengthFieldPrepender(3));
                        // 5. Velocity forwarding handler — after framing, before handshake decoder
                        if (bridge.isEnabled()
                                && bridge.getConfig().getPlatform().usesModernForwarding()) {
                            ch.pipeline().addLast(
                                    "velocity-forwarding",
                                    bridge.createVelocityHandler());
                        }
                        // 6. Handshake → version negotiation → optional translation pipeline
                        ch.pipeline().addLast("handshake",
                                new net.lega.protocol.handler.HandshakePacketDecoder(negotiator));
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        LOGGER.info("LEGA listening on port {} ({})",
                port,
                bridge.isEnabled()
                    ? bridge.getConfig().getPlatform().getDisplayName() + " mode"
                    : "standalone");
    }

    /**
     * Gracefully shuts down the Netty server, waiting up to 5 seconds for
     * in-flight operations to complete.
     */
    public void stop() {
        LOGGER.info("Stopping network layer...");
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).awaitUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).awaitUninterruptibly();
        }
        LOGGER.info("Network layer stopped.");
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isOpen();
    }

    public int getPort() {
        return port;
    }
}

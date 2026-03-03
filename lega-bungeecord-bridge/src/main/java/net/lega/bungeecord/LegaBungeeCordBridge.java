package net.lega.bungeecord;

/**
 * Entry point for the LEGA Proxy Bridge.
 *
 * <p>Supports the following proxy platforms:</p>
 * <ul>
 *   <li><b>BungeeCord / Waterfall / Travertine</b> – legacy IP forwarding via the
 *       NUL-delimited server-address field in the Handshake packet, with optional
 *       BungeeGuard token verification.</li>
 *   <li><b>Velocity</b> – modern forwarding via a Login Plugin Request/Response
 *       exchange in the Login phase, signed with HMAC-SHA256 and a shared secret
 *       configured in {@code proxy.yml} under {@code velocity-support.secret}.</li>
 * </ul>
 *
 * <h3>Pipeline integration — BungeeCord platforms</h3>
 * <pre>
 * if (bridge.isEnabled() &amp;&amp; !bridge.getConfig().getPlatform().usesModernForwarding()) {
 *     ch.pipeline().addFirst("proxy-interceptor", bridge.createInterceptor());
 * }
 * </pre>
 *
 * <h3>Pipeline integration — Velocity</h3>
 * <pre>
 * if (bridge.isEnabled() &amp;&amp; bridge.getConfig().getPlatform().usesModernForwarding()) {
 *     ch.pipeline().addFirst("velocity-forwarding", bridge.createVelocityHandler());
 * }
 * </pre>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.bungeecord.proxy.BungeeCordBridgeConfig;
import net.lega.bungeecord.proxy.BungeeCordHandshakeInterceptor;
import net.lega.bungeecord.proxy.ProxyPlatform;
import net.lega.bungeecord.velocity.VelocityForwardingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class LegaBungeeCordBridge {

    private static final Logger LOG = LoggerFactory.getLogger(LegaBungeeCordBridge.class);

    private final BungeeCordBridgeConfig config;
    private boolean initialized = false;

    public LegaBungeeCordBridge(Path serverRoot) {
        this.config = new BungeeCordBridgeConfig(serverRoot);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Loads the proxy config and validates it.  Must be called before
     * {@link #createInterceptor()}.
     *
     * @throws IOException           if proxy.yml cannot be read or created
     * @throws IllegalStateException if BungeeGuard is enabled but no tokens
     *                               are configured
     */
    public void initialize() throws IOException {
        config.load();
        if (config.isEnabled()) {
            validateConfig();
            printBridgeBanner();
        }
        initialized = true;
    }

    /**
     * Returns {@code true} if the bridge is active (proxy.yml → enabled: true).
     */
    public boolean isEnabled() {
        ensureInitialized();
        return config.isEnabled();
    }

    /**
     * Creates a fresh {@link BungeeCordHandshakeInterceptor} for a new incoming
     * channel (BungeeCord / Waterfall / Travertine only).
     *
     * @return new interceptor instance
     * @throws IllegalStateException if bridge is disabled or platform is Velocity
     */
    public BungeeCordHandshakeInterceptor createInterceptor() {
        ensureInitialized();
        if (!config.isEnabled()) {
            throw new IllegalStateException(
                    "Cannot create interceptor: proxy bridge is disabled in proxy.yml");
        }
        if (config.getPlatform().usesModernForwarding()) {
            throw new IllegalStateException(
                    "Platform is VELOCITY — use createVelocityHandler() instead of createInterceptor()");
        }
        return new BungeeCordHandshakeInterceptor(config);
    }

    /**
     * Creates a fresh {@link VelocityForwardingHandler} for a new incoming channel
     * (Velocity only).
     *
     * <p>Each channel must receive its own instance; the handler is stateful and
     * removes itself from the pipeline after processing one Login sequence.</p>
     *
     * @return new Velocity forwarding handler
     * @throws IllegalStateException if bridge is disabled or platform is not Velocity
     */
    public VelocityForwardingHandler createVelocityHandler() {
        ensureInitialized();
        if (!config.isEnabled()) {
            throw new IllegalStateException(
                    "Cannot create Velocity handler: proxy bridge is disabled in proxy.yml");
        }
        if (!config.getPlatform().usesModernForwarding()) {
            throw new IllegalStateException(
                    "Platform is " + config.getPlatform().name()
                    + " — use createInterceptor() instead of createVelocityHandler()");
        }
        return new VelocityForwardingHandler(config.getVelocitySecret());
    }

    public BungeeCordBridgeConfig getConfig() {
        return config;
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void validateConfig() {
        ProxyPlatform platform = config.getPlatform();

        if (platform.usesModernForwarding()) {
            // Velocity-specific validation
            String secret = config.getVelocitySecret();
            if (secret.isBlank()) {
                throw new IllegalStateException(
                        "proxy.yml: platform=VELOCITY but velocity-support.secret is not set. "
                      + "Add the shared secret from velocity.toml (forwarding-secret).");
            }
            if (secret.equalsIgnoreCase("SEU_TOKEN_AQUI") || secret.equalsIgnoreCase("CHANGE_ME")) {
                LOG.warn("proxy.yml: velocity-support.secret is still the default placeholder! "
                       + "Replace it with the actual forwarding-secret from velocity.toml.");
            }
        } else {
            // BungeeCord / Waterfall / Travertine validation
            if (config.isBungeeGuardEnabled() && config.getBungeeGuardTokens().isEmpty()) {
                throw new IllegalStateException(
                        "proxy.yml: bungee-guard.enabled is true but no tokens are configured. "
                      + "Add at least one token under bungee-guard.tokens.");
            }
            if (config.isBungeeGuardEnabled()) {
                for (String token : config.getBungeeGuardTokens()) {
                    if (token.equals("CHANGE_ME_TOKEN_1") || token.equals("ROTATION_TOKEN_2")) {
                        LOG.warn("proxy.yml: BungeeGuard token is still the default placeholder! "
                               + "Replace it with a secure random string.");
                    }
                }
            }
        }
    }

    private void printBridgeBanner() {
        ProxyPlatform p = config.getPlatform();
        LOG.info("╔══════════════════════════════════════════════════╗");
        LOG.info("║  LEGA {} Bridge ACTIVE", String.format("%-14s", p.getDisplayName()));
        if (p.usesModernForwarding()) {
            LOG.info("║  Mode           : Velocity Modern Forwarding (v4)");
            String sec = config.getVelocitySecret();
            String masked = sec.length() > 4
                    ? "*".repeat(sec.length() - 2) + sec.substring(sec.length() - 2)
                    : "****";
            LOG.info("║  Secret         : {}", masked);
        } else {
            LOG.info("║  IP Forwarding  : {}", config.isIpForwardingEnabled());
            LOG.info("║  BungeeGuard    : {} (tokens: {})",
                    config.isBungeeGuardEnabled(),
                    config.getBungeeGuardTokens().size());
        }
        LOG.info("║  Reject direct  : {}", config.isRejectDirectConnections());
        LOG.info("╚══════════════════════════════════════════════════╝");
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "LegaBungeeCordBridge.initialize() has not been called yet.");
        }
    }
}

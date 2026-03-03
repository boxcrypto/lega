package net.lega.bungeecord.proxy;

/**
 * Loads and holds the {@code proxy.yml} configuration for the proxy bridge.
 * Supports BungeeCord, Waterfall, Travertine (legacy IP forwarding) and
 * Velocity (modern forwarding with HMAC-SHA256 signed Login Plugin Request).
 *
 * <p>File location: {@code ./proxy.yml} in the server root.</p>
 *
 * <h3>Default proxy.yml</h3>
 * <pre>
 * # LEGA Proxy Bridge Configuration
 * enabled: false
 * platform: BUNGEECORD       # BUNGEECORD | WATERFALL | TRAVERTINE | VELOCITY
 * ip-forwarding: true        # Enable BungeeCord IP forwarding (unused for VELOCITY)
 * bungee-guard:
 *   enabled: false
 *   tokens:
 *     - "CHANGE_ME"          # One token per proxy; multiple for key rotation
 * online-mode: true          # Use proxy's auth result
 * reject-direct-connections: true
 * velocity-support:
 *   secret: "SEU_TOKEN_AQUI" # Must match forwarding-secret in velocity.toml
 * </pre>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class BungeeCordBridgeConfig {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/ProxyBridge");
    private static final String FILE_NAME = "proxy.yml";

    private final Path filePath;

    // ── parsed fields ─────────────────────────────────────────────────────────
    private boolean enabled                = false;
    private ProxyPlatform platform         = ProxyPlatform.BUNGEECORD;
    private boolean ipForwarding           = true;
    private boolean bungeeGuardEnabled     = false;
    private List<String> bungeeGuardTokens = new ArrayList<>();
    private boolean onlineMode             = true;
    private boolean rejectDirect           = true;
    private String velocitySecret          = "";

    public BungeeCordBridgeConfig(Path serverRoot) {
        this.filePath = serverRoot.resolve(FILE_NAME);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    public void load() throws IOException {
        if (!Files.exists(filePath)) {
            createDefault();
            return;
        }
        Map<String, Object> raw = loadYaml(filePath);

        enabled      = booleanOf(raw, "enabled", false);
        platform     = parsePlatform(String.valueOf(raw.getOrDefault("platform", "BUNGEECORD")));
        ipForwarding = booleanOf(raw, "ip-forwarding", true);
        onlineMode   = booleanOf(raw, "online-mode", true);
        rejectDirect = booleanOf(raw, "reject-direct-connections", true);

        Object bgSection = raw.get("bungee-guard");
        if (bgSection instanceof Map<?,?> bgMap) {
            bungeeGuardEnabled = booleanOf(bgMap, "enabled", false);
            Object tokenList = bgMap.get("tokens");
            if (tokenList instanceof List<?> tl) {
                bungeeGuardTokens = new ArrayList<>();
                for (Object t : tl) bungeeGuardTokens.add(String.valueOf(t).trim());
            }
        }

        Object vsSection = raw.get("velocity-support");
        if (vsSection instanceof Map<?,?> vsMap) {
            velocitySecret = String.valueOf(vsMap.getOrDefault("secret", "")).trim();
        }

        if (enabled) {
            LOGGER.info("Proxy bridge ENABLED  platform={} ipForwarding={} bungeeGuard={}",
                    platform.getDisplayName(), ipForwarding, bungeeGuardEnabled);
        }
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public boolean isEnabled()                  { return enabled; }
    public ProxyPlatform getPlatform()           { return platform; }
    public boolean isIpForwardingEnabled()       { return ipForwarding; }
    public boolean isBungeeGuardEnabled()        { return bungeeGuardEnabled; }
    public List<String> getBungeeGuardTokens()  { return Collections.unmodifiableList(bungeeGuardTokens); }
    public boolean isOnlineMode()                { return onlineMode; }
    public boolean isRejectDirectConnections()   { return rejectDirect; }

    /**
     * Returns the Velocity modern forwarding shared secret configured under
     * {@code velocity-support.secret} in {@code proxy.yml}.  Empty string if
     * not configured.
     */
    public String getVelocitySecret()            { return velocitySecret; }

    // ── private helpers ───────────────────────────────────────────────────────

    private static ProxyPlatform parsePlatform(String raw) {
        for (ProxyPlatform p : ProxyPlatform.values()) {
            if (p.name().equalsIgnoreCase(raw.trim())) return p;
        }
        LOGGER.warn("Unknown proxy platform '{}', defaulting to BUNGEECORD", raw);
        return ProxyPlatform.BUNGEECORD;
    }

    private static boolean booleanOf(Map<?,?> map, String key, boolean def) {
        Object v = map.get(key);
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path path) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(opts);
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, Object> m = yaml.load(r);
            return m != null ? m : new LinkedHashMap<>();
        }
    }

    private void createDefault() throws IOException {
        Files.createDirectories(filePath.getParent());
        List<String> lines = List.of(
            "# ============================================================",
            "# LEGA Proxy Bridge Configuration",
            "# Supports: BungeeCord, Waterfall, Travertine, Velocity",
            "# ============================================================",
            "",
            "# Set to true to enable the proxy bridge.",
            "# When enabled, LEGA trusts the forwarded IP and UUID from the proxy.",
            "enabled: false",
            "",
            "# Proxy platform: BUNGEECORD | WATERFALL | TRAVERTINE | VELOCITY",
            "platform: BUNGEECORD",
            "",
            "# Enable BungeeCord IP forwarding (reads real IP/UUID from Handshake packet).",
            "# Not used when platform is VELOCITY.",
            "ip-forwarding: true",
            "",
            "# ── BungeeGuard (BungeeCord / Waterfall / Travertine only) ─────────────────",
            "# Verify the BungeeGuard token to prevent unauthorized direct connections.",
            "bungee-guard:",
            "  enabled: false",
            "  tokens:",
            "    - \"CHANGE_ME_TOKEN_1\"   # Replace with your BungeeGuard token",
            "    #- \"ROTATION_TOKEN_2\"   # Optional: add more for zero-downtime token rotation",
            "",
            "# Treat proxied players as authenticated (uses proxy's online-mode result)",
            "online-mode: true",
            "",
            "# Reject connections not coming through the proxy.",
            "# For BungeeCord: rejects clients with no NUL-injected forwarding data.",
            "# For Velocity  : rejects clients that don't respond to Login Plugin Request.",
            "reject-direct-connections: true",
            "",
            "# ── Velocity Modern Forwarding ─────────────────────────────────────────────",
            "# Only used when platform is set to VELOCITY.",
            "# secret must exactly match 'forwarding-secret' in velocity.toml.",
            "velocity-support:",
            "  secret: \"SEU_TOKEN_AQUI\"",
            ""
        );
        Files.write(filePath, lines, java.nio.charset.StandardCharsets.UTF_8);
        LOGGER.info("Created default {}", FILE_NAME);
    }
}

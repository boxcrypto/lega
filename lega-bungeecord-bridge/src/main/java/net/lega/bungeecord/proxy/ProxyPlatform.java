package net.lega.bungeecord.proxy;

/**
 * Identifies which proxy platform is forwarding a player connection to LEGA.
 *
 * <p>BungeeCord, Waterfall, and Travertine use the legacy IP-forwarding protocol
 * (NUL-delimited server-address field in the Handshake packet).  Velocity uses its
 * own <em>modern forwarding</em> protocol: a Login Plugin Request/Response exchange
 * in the Login phase, signed with HMAC-SHA256 and the shared secret.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public enum ProxyPlatform {

    /**
     * Original BungeeCord proxy by SpigotMC.
     * Uses legacy NUL-delimited IP forwarding; no BungeeGuard by default.
     */
    BUNGEECORD("BungeeCord", false, false),

    /**
     * Waterfall – PaperMC fork of BungeeCord.
     * Supports optional BungeeGuard token verification.
     */
    WATERFALL("Waterfall", true, false),

    /**
     * Travertine – PaperMC fork of Waterfall with broader protocol support.
     * Supports BungeeGuard token verification.
     */
    TRAVERTINE("Travertine", true, false),

    /**
     * Velocity – modern proxy by PaperMC.
     * Uses Login Plugin Request/Response modern forwarding (HMAC-SHA256 signed),
     * completely separate from the BungeeCord IP-forwarding mechanism.
     */
    VELOCITY("Velocity", false, true);

    private final String displayName;
    private final boolean supportsBungeeGuard;
    private final boolean usesModernForwarding;

    ProxyPlatform(String displayName, boolean supportsBungeeGuard, boolean usesModernForwarding) {
        this.displayName = displayName;
        this.supportsBungeeGuard = supportsBungeeGuard;
        this.usesModernForwarding = usesModernForwarding;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Returns {@code true} if this platform supports BungeeGuard token verification. */
    public boolean supportsBungeeGuard() {
        return supportsBungeeGuard;
    }

    /**
     * Returns {@code true} if this platform uses Velocity modern forwarding
     * (Login Plugin Request/Response, HMAC-SHA256) instead of the BungeeCord
     * NUL-delimited handshake injection.
     */
    public boolean usesModernForwarding() {
        return usesModernForwarding;
    }
}

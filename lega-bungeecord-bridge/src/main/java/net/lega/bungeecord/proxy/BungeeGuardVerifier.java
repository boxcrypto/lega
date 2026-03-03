package net.lega.bungeecord.proxy;

/**
 * Verifies the BungeeGuard authentication token appended to the BungeeCord
 * handshake forwarding data.
 *
 * <h3>BungeeGuard protocol</h3>
 * <p>When BungeeGuard is active on the proxy, it appends a fifth NUL-delimited
 * field to the server-address string:</p>
 * <pre>
 *   hostname\x00realIp\x00uuid\x00properties\x00{bungeeGuardToken}
 * </pre>
 * <p>The token is a plain shared secret (configured identically on the proxy
 * and on each backend server).  Multiple tokens are supported for zero-downtime
 * key rotation — a connection is accepted if the received token matches
 * <em>any</em> configured token.</p>
 *
 * <p>Comparison is performed in constant time to prevent timing-based attacks.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public final class BungeeGuardVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(BungeeGuardVerifier.class);

    private final List<String> allowedTokens;

    public BungeeGuardVerifier(List<String> allowedTokens) {
        if (allowedTokens == null || allowedTokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "BungeeGuard is enabled but no tokens configured " +
                    "— add at least one token to proxy.yml → bungee-guard.tokens");
        }
        this.allowedTokens = List.copyOf(allowedTokens);
    }

    // ── public API ─────────────────────────────────────────────────────────────

    /**
     * Verifies that {@code receivedToken} matches one of the configured tokens.
     *
     * @param receivedToken  the token extracted from the handshake
     * @param remoteAddress  logging context only
     * @return {@code true} if the token is valid; {@code false} if rejected
     */
    public boolean verify(String receivedToken, String remoteAddress) {
        if (receivedToken == null || receivedToken.isBlank()) {
            LOG.warn("[{}] BungeeGuard: no token in handshake — rejecting", remoteAddress);
            return false;
        }

        byte[] received = receivedToken.getBytes(StandardCharsets.UTF_8);

        for (String configured : allowedTokens) {
            byte[] expected = configured.getBytes(StandardCharsets.UTF_8);
            if (constantTimeEquals(received, expected)) {
                LOG.debug("[{}] BungeeGuard: token verified", remoteAddress);
                return true;
            }
        }

        LOG.warn("[{}] BungeeGuard: invalid token — rejecting connection", remoteAddress);
        return false;
    }

    // ── private ────────────────────────────────────────────────────────────────

    /**
     * Constant-time byte-array comparison to prevent timing attacks.
     * Uses {@link MessageDigest#isEqual(byte[], byte[])} which is specified
     * to run in time proportional to the length of the <em>first</em> argument.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            // XOR compare with padding to maintain constant time
            byte[] padded = new byte[a.length];
            int copyLen = Math.min(a.length, b.length);
            System.arraycopy(b, 0, padded, 0, copyLen);
            MessageDigest.isEqual(a, padded); // run to keep timing constant
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}

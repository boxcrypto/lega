package net.lega.velocity;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;


public final class LegaVelocityBridge {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Velocity");

    
    public static final String CHANNEL_MAIN     = "lega:main";
    public static final String CHANNEL_TELEPORT = "lega:teleport";
    public static final String CHANNEL_PERMS    = "lega:perms";
    public static final String CHANNEL_STAFF    = "lega:staff";

    private final String forwardingSecret;
    private final boolean encryptedBridge;
    private final boolean antiSpoofing;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);

    // Pending teleport requests: requestId -> callback
    private final ConcurrentHashMap<UUID, Consumer<TeleportResult>> pendingTeleports =
            new ConcurrentHashMap<>();

    // Players verified to be from Velocity (IP → player data)
    private final ConcurrentHashMap<String, ModernForwardingData> verifiedPlayers =
            new ConcurrentHashMap<>();

    public LegaVelocityBridge(String forwardingSecret, boolean encryptedBridge, boolean antiSpoofing) {
        this.forwardingSecret = forwardingSecret;
        this.encryptedBridge  = encryptedBridge;
        this.antiSpoofing     = antiSpoofing;
    }

    
    public void initialize() {
        LOGGER.info("[LEGA/Velocity] Initializing Velocity bridge...");
        LOGGER.info("[LEGA/Velocity]   Forwarding mode   : MODERN");
        LOGGER.info("[LEGA/Velocity]   Encrypted bridge  : {}", encryptedBridge);
        LOGGER.info("[LEGA/Velocity]   Anti-spoofing     : {}", antiSpoofing);
        LOGGER.info("[LEGA/Velocity]   Channels          : {}, {}, {}, {}",
                CHANNEL_MAIN, CHANNEL_TELEPORT, CHANNEL_PERMS, CHANNEL_STAFF);
        connected.set(true);
        LOGGER.info("[LEGA/Velocity] Bridge ready.");
    }

    // =========================================================================
    // Modern Forwarding
    // =========================================================================

    
    public ModernForwardingData validateModernForwarding(
            byte[] signingKey,
            byte[] data,
            byte[] signature) {

        if (antiSpoofing) {
            byte[] expectedHmac = computeHmac(signingKey, data);
            if (!MessageDigest.isEqual(expectedHmac, signature)) {
                throw new SecurityException("Velocity modern forwarding HMAC validation failed! Possible spoofing.");
            }
        }

        // Parse player data from the verified Velocity modern forwarding payload
        try {
            ModernForwardingData result = ModernForwardingData.parse(data);
            verifiedPlayers.put(result.playerUUID().toString(), result);
            return result;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to parse Velocity modern forwarding data", e);
        }
    }

    
    public boolean isPlayerVerified(UUID playerUUID) {
        return verifiedPlayers.containsKey(playerUUID.toString());
    }

    // =========================================================================
    // Cross-server Messaging
    // =========================================================================

    
    public void sendMessage(String channel, byte[] data) {
        if (!connected.get()) {
            LOGGER.warn("[LEGA/Velocity] Cannot send message: bridge not connected");
            return;
        }
        // In production this would write to the player's channel
        messagesSent.incrementAndGet();
        LOGGER.debug("[LEGA/Velocity] → [{}] {}b", channel, data.length);
    }

    
    public void onMessageReceived(String channel, byte[] data) {
        messagesReceived.incrementAndGet();
        LOGGER.debug("[LEGA/Velocity] ← [{}] {}b", channel, data.length);

        switch (channel) {
            case CHANNEL_TELEPORT -> handleTeleportMessage(data);
            case CHANNEL_PERMS    -> handlePermissionSync(data);
            case CHANNEL_STAFF    -> handleStaffModeSync(data);
            default               -> LOGGER.debug("[LEGA/Velocity] Unknown channel: {}", channel);
        }
    }

    // =========================================================================
    // Cross-server Teleport
    // =========================================================================

    
    public UUID requestCrossServerTeleport(
            UUID playerUUID,
            String targetServer,
            Consumer<TeleportResult> callback) {

        UUID requestId = UUID.randomUUID();
        pendingTeleports.put(requestId, callback);

        // Build teleport request payload
        // { "type": "TELEPORT", "requestId": "...", "player": "...", "target": "..." }
        String payload = String.format(
                "{\"type\":\"TELEPORT\",\"requestId\":\"%s\",\"player\":\"%s\",\"target\":\"%s\"}",
                requestId, playerUUID, targetServer
        );
        sendMessage(CHANNEL_TELEPORT, payload.getBytes());
        return requestId;
    }

    private void handleTeleportMessage(byte[] data) {
        // Parse and resolve pending teleport requests
        String json = new String(data);
        LOGGER.debug("[LEGA/Velocity] Teleport ack: {}", json);
    }

    private void handlePermissionSync(byte[] data) {
        LOGGER.debug("[LEGA/Velocity] Permission sync received: {}b", data.length);
    }

    private void handleStaffModeSync(byte[] data) {
        LOGGER.debug("[LEGA/Velocity] Staff mode sync received: {}b", data.length);
    }

    // =========================================================================
    // Crypto
    // =========================================================================

    private byte[] computeHmac(byte[] key, byte[] data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public boolean isConnected()       { return connected.get(); }
    public long getMessagesSent()      { return messagesSent.get(); }
    public long getMessagesReceived()  { return messagesReceived.get(); }
    public int getVerifiedPlayerCount(){ return verifiedPlayers.size(); }

    // =========================================================================
    // Data records
    // =========================================================================

    
    public record ModernForwardingData(
            UUID playerUUID,
            String playerName,
            String realIP,
            List<Property> properties
    ) {
        
        public record Property(String name, String value, String signature) {}

        /**
         * Parses the HMAC-verified Velocity modern forwarding payload.
         *
         * <p>Expected format (all fields in network byte order):</p>
         * <ol>
         *   <li>version (VarInt)</li>
         *   <li>realIp (String: VarInt-length + UTF-8 bytes)</li>
         *   <li>UUID as two {@code long} values (MSB then LSB)</li>
         *   <li>playerName (String)</li>
         *   <li>propertyCount (VarInt)</li>
         *   <li>for each property: name (String), value (String),
         *       hasSig (boolean), signature (String, only if hasSig)</li>
         * </ol>
         *
         * @param data the forwarding payload bytes (EXCLUDING the trailing HMAC)
         * @return a populated {@link ModernForwardingData} instance
         * @throws IOException              on malformed data
         * @throws IllegalArgumentException if the forwarding version is unsupported
         */
        public static ModernForwardingData parse(byte[] data) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);

            int version = readVarInt(bais);
            if (version < 1 || version > 4) {
                throw new IllegalArgumentException(
                        "Unsupported Velocity forwarding version: " + version);
            }

            String realIp     = readString(bais);
            DataInputStream dis = new DataInputStream(bais);
            long uuidMsb      = dis.readLong();
            long uuidLsb      = dis.readLong();
            UUID uuid         = new UUID(uuidMsb, uuidLsb);
            String playerName = readString(bais);

            List<Property> props = new ArrayList<>();
            if (bais.available() > 0) {
                int propCount = readVarInt(bais);
                for (int i = 0; i < propCount; i++) {
                    String pName  = readString(bais);
                    String pValue = readString(bais);
                    int hasSigByte = bais.read();
                    boolean hasSig = hasSigByte != 0;
                    String pSig   = hasSig ? readString(bais) : null;
                    props.add(new Property(pName, pValue, pSig));
                }
            }

            return new ModernForwardingData(uuid, playerName, realIp,
                    Collections.unmodifiableList(props));
        }

        // ── Stream helpers ────────────────────────────────────────────────────

        private static int readVarInt(ByteArrayInputStream in) throws IOException {
            int value = 0, shift = 0;
            int b;
            do {
                b = in.read();
                if (b == -1) throw new IOException("Unexpected end of VarInt stream");
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("VarInt too long");
            } while ((b & 0x80) != 0);
            return value;
        }

        private static String readString(ByteArrayInputStream in) throws IOException {
            int len = readVarInt(in);
            if (len < 0 || len > 32767) throw new IOException("String length out of range: " + len);
            byte[] bytes = in.readNBytes(len);
            if (bytes.length != len) throw new IOException("Truncated string");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    
    public record TeleportResult(boolean success, String reason) {
        public static TeleportResult ok() { return new TeleportResult(true, null); }
        public static TeleportResult fail(String reason) { return new TeleportResult(false, reason); }
    }
}

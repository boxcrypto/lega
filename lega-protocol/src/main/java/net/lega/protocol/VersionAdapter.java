package net.lega.protocol;

import io.netty.buffer.ByteBuf;

import java.util.Set;

/**
 * Contract that every version-specific protocol adapter must fulfil.
 *
 * <p>A {@code VersionAdapter} encapsulates all differences between
 * Minecraft protocol versions: packet IDs, data encoding formats,
 * feature availability checks, and payload translation.</p>
 *
 * <pre>{@code
 * VersionAdapter adapter = VersionAdapterRegistry.get().bestFor(MinecraftVersion.V1_20_4);
 * byte[] encoded = adapter.encodeChat(ChatMessage.of("Hello, world!"));
 * }</pre>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public interface VersionAdapter {

    // ── identity ──────────────────────────────────────────────────────────

    /** Primary version this adapter was written for. */
    MinecraftVersion primaryVersion();

    /** All versions handled by this adapter instance. */
    Set<MinecraftVersion> supportedVersions();

    /** Version family (determines packet-set grouping). */
    default VersionFamily family() { return primaryVersion().getFamily(); }

    // ── feature flags ─────────────────────────────────────────────────────

    /** Whether off-hand / dual-wield is supported (≥ 1.9). */
    boolean supportsOffHand();

    /** Whether the flattened block ID registry is used (≥ 1.13). */
    boolean supportsFlattening();

    /** Whether 1.13+ adventure / chat components are used. */
    boolean supportsChatComponents();

    /** Whether chat messages are signed (≥ 1.19). */
    boolean supportsChatSigning();

    /** Whether item data uses NBT component format (≥ 1.20.5). */
    boolean supportsItemComponents();

    /** Whether the chunk format uses the -64 to 320 height range (≥ 1.18). */
    boolean supportsNewChunkFormat();

    /** Whether the Tab-List uses UUID + name-only display (≥ 1.8). */
    boolean supportsUUID();

    /** Whether the action-bar packet exists (≥ 1.8). */
    boolean supportsActionBar();

    /** Whether boss-bar packets are native (≥ 1.9; previously NPC hack). */
    boolean supportsBossBar();

    /** Whether vanilla titles are supported (≥ 1.8). */
    boolean supportsTitles();

    /** Whether hex colour codes are supported in text (≥ 1.16). */
    boolean supportsHexColour();

    /** Whether the bundle packet exists (≥ 1.19.4). */
    boolean supportsBundle();

    /** Whether the "Enforce Secure Chat" handshake field exists (≥ 1.19.1). */
    boolean supportsSecureChatHello();

    /** Whether the server list icon is supported via MOTD ping. */
    boolean supportsServerIcon();

    // ── packet ID lookups ─────────────────────────────────────────────────

    /** Clientbound chat message packet ID. */
    int packetIdChatMessage();

    /** Clientbound chunk data packet ID. */
    int packetIdChunkData();

    /** Clientbound entity position packet ID. */
    int packetIdEntityPosition();

    /** Clientbound player list header/footer packet ID. */
    int packetIdTabListHeaderFooter();

    /** Clientbound boss bar packet ID (−1 if unsupported). */
    int packetIdBossBar();

    /** Clientbound title packet ID (−1 if unsupported). */
    int packetIdTitle();

    /** Clientbound action bar packet ID (−1 if unsupported). */
    int packetIdActionBar();

    /** Clientbound disconnect (play) packet ID. */
    int packetIdDisconnect();

    /** Clientbound spawn entity packet ID. */
    int packetIdSpawnEntity();

    /** Clientbound entity metadata packet ID. */
    int packetIdEntityMetadata();

    /** Serverbound chat packet ID (client → server). */
    int packetIdServerboundChat();

    /** Serverbound player position packet ID. */
    int packetIdServerboundPosition();

    // ── encoding helpers ──────────────────────────────────────────────────

    /**
     * Encodes a plain-text chat message into the correct wire format
     * for this protocol version.
     *
     * @param  message plain or JSON text
     * @param  position 0 = chat, 1 = system, 2 = action bar
     * @return encoded bytes ready for transmission
     */
    byte[] encodeChat(String message, int position);

    /**
     * Encodes a title packet payload.
     *
     * @param title    main title JSON or null
     * @param subtitle subtitle JSON or null
     * @param fadeIn   ticks
     * @param stay     ticks
     * @param fadeOut  ticks
     * @return encoded bytes (may span multiple packets on old versions)
     */
    byte[] encodeTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /**
     * Encodes a disconnect reason into the correct JSON/text format.
     *
     * @param reason human-readable reason
     * @return encoded disconnect payload
     */
    byte[] encodeDisconnect(String reason);

    /**
     * Translates a raw block state ID from the native protocol format
     * of this version into the modern (1.18+) unified block state ID.
     *
     * @param  raw raw block state integer from the wire
     * @return modern block state ID
     */
    int translateBlockState(int raw);

    /**
     * Reads a variable-length integer from {@code buf} using the
     * encoding scheme of this protocol version.
     *
     * <p>1.7.x used a different VarInt scheme; all later versions
     * use the standard unsigned LEB-128 encoding.</p>
     */
    int readVarInt(ByteBuf buf);

    /**
     * Writes a variable-length integer to {@code buf}.
     */
    void writeVarInt(ByteBuf buf, int value);

    /**
     * Reads a player position payload from a movement packet.
     *
     * @return array of [x, y, z, yaw, pitch]
     */
    double[] readPosition(ByteBuf buf);

    /**
     * Writes a player position payload compatible with this version.
     */
    void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch);

    // ── validation ────────────────────────────────────────────────────────

    /**
     * Returns whether {@code version} is handled by this adapter.
     * Adapters may cover a range of versions.
     */
    default boolean handles(MinecraftVersion version) {
        return supportedVersions().contains(version);
    }

    /**
     * Returns a short debug description.
     */
    default String describe() {
        return getClass().getSimpleName() + "[" + primaryVersion().getDisplayName() + "]";
    }
}

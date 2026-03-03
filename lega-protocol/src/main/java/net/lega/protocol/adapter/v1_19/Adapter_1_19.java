package net.lega.protocol.adapter.v1_19;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Protocol adapter for Minecraft 1.19 through 1.19.4 (The Wild Update).
 *
 * <h2>Major changes from 1.18</h2>
 * <ul>
 *   <li><b>Chat signing</b> (1.19): Messages are signed with the player's
 *       private RSA key. The server receives and verifies signatures.</li>
 *   <li><b>Enforce Secure Chat</b> (1.19.1): Server can force signed chat
 *       or disconnect unsigned clients.</li>
 *   <li>Frogs, tadpoles, allay, warden, ancient city structure.</li>
 *   <li>Mangrove swamp biome and mangrove wood set.</li>
 *   <li>Deep Dark biome with sculk blocks and sculk sensors.</li>
 *   <li>Mud, packed mud, mud bricks added.</li>
 *   <li>1.19.3: Bundle packet experiment continues; chat type registry added.</li>
 *   <li>1.19.4: Bundle delimiter packet introduced (0x00).</li>
 *   <li>Chat packet split into player-chat and system-chat. </li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_19 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_PLAYER_CHAT         = 0x30;   // signed player chat
    private static final int PKT_SYSTEM_CHAT         = 0x62;   // system messages
    private static final int PKT_ACTION_BAR          = 0x41;
    private static final int PKT_CHUNK_DATA          = 0x22;
    private static final int PKT_ENTITY_POSITION     = 0x2A;
    private static final int PKT_TAB_LIST            = 0x60;
    private static final int PKT_BOSS_BAR            = 0x0D;
    private static final int PKT_TITLE_TEXT          = 0x5A;
    private static final int PKT_TITLE_SUBTITLE      = 0x58;
    private static final int PKT_TITLE_TIMES         = 0x5B;
    private static final int PKT_DISCONNECT          = 0x17;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x4E;
    private static final int PKT_BUNDLE_DELIMITER    = 0x00;   // 1.19.4+

    private static final int PKT_SB_CHAT             = 0x04;   // signed chat
    private static final int PKT_SB_CHAT_COMMAND     = 0x03;   // /command
    private static final int PKT_SB_POSITION         = 0x13;

    public Adapter_1_19() {
        super(
            MinecraftVersion.V1_19,
            MinecraftVersion.V1_19_1,
            MinecraftVersion.V1_19_2,
            MinecraftVersion.V1_19_3,
            MinecraftVersion.V1_19_4
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_19_4; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()             { return true; }
    @Override public boolean supportsActionBar()        { return true; }
    @Override public boolean supportsBossBar()          { return true; }
    @Override public boolean supportsTitles()           { return true; }
    @Override public boolean supportsOffHand()          { return true; }
    @Override public boolean supportsFlattening()       { return true; }
    @Override public boolean supportsChatComponents()   { return true; }
    @Override public boolean supportsHexColour()        { return true; }
    @Override public boolean supportsNewChunkFormat()   { return true; }
    @Override public boolean supportsChatSigning()      { return true; }   // KEY: chat signing
    @Override public boolean supportsSecureChatHello()  { return true; }
    @Override public boolean supportsBundle()           { return true; }   // 1.19.4
    @Override public boolean supportsServerIcon()       { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_PLAYER_CHAT; }
    @Override public int packetIdChunkData()             { return PKT_CHUNK_DATA; }
    @Override public int packetIdEntityPosition()        { return PKT_ENTITY_POSITION; }
    @Override public int packetIdTabListHeaderFooter()   { return PKT_TAB_LIST; }
    @Override public int packetIdBossBar()               { return PKT_BOSS_BAR; }
    @Override public int packetIdTitle()                 { return PKT_TITLE_TEXT; }
    @Override public int packetIdActionBar()             { return PKT_ACTION_BAR; }
    @Override public int packetIdDisconnect()            { return PKT_DISCONNECT; }
    @Override public int packetIdSpawnEntity()           { return PKT_SPAWN_ENTITY; }
    @Override public int packetIdEntityMetadata()        { return PKT_ENTITY_METADATA; }
    @Override public int packetIdServerboundChat()       { return PKT_SB_CHAT; }
    @Override public int packetIdServerboundPosition()   { return PKT_SB_POSITION; }

    // ── encoding ──────────────────────────────────────────────────────────

    /**
     * Encodes a <em>system</em> chat message (unsigned, no signature).
     * Use this for server-originated messages.
     *
     * @param message plain or JSON text
     * @param overlay true = action bar, false = chat box
     */
    public byte[] encodeSystemChat(String message, boolean overlay) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_SYSTEM_CHAT);
            writeString(buf, toJson(message));
            buf.writeBoolean(overlay);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /** Delegates to system-chat packet for position 0/1. Action bar for 2. */
    @Override
    public byte[] encodeChat(String message, int position) {
        return encodeSystemChat(message, position == 2);
    }

    @Override
    public byte[] encodeTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_TITLE_TIMES);
            buf.writeInt(fadeIn); buf.writeInt(stay); buf.writeInt(fadeOut);
            if (title != null) {
                writeVarInt(buf, PKT_TITLE_TEXT);
                writeString(buf, toJson(title));
            }
            if (subtitle != null) {
                writeVarInt(buf, PKT_TITLE_SUBTITLE);
                writeString(buf, toJson(subtitle));
            }
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * Verifies a player chat signature hash (SHA-256 of timestamp + message).
     * This is a simplified verification — full Mojang RSA chain validation
     * requires the public key from the session server.
     *
     * @param messageBytes raw message bytes
     * @param timestamp    Instant the message was signed
     * @param signature    the 256-byte RSA signature from the packet
     * @return true if the SHA-256 hash prefix matches (lightweight check)
     */
    public boolean verifyChatSignatureLightweight(byte[] messageBytes, Instant timestamp, byte[] signature) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(messageBytes);
            sha.update(Long.toString(timestamp.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha.digest();
            // Compare first 4 bytes as a lightweight check
            if (signature.length < 4) return false;
            return hash[0] == signature[0] && hash[1] == signature[1]
                && hash[2] == signature[2] && hash[3] == signature[3];
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    @Override
    public double[] readPosition(ByteBuf buf) {
        double x = buf.readDouble(); double y = buf.readDouble(); double z = buf.readDouble();
        float yaw = buf.readFloat(); float pitch = buf.readFloat(); buf.readBoolean();
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(yaw); buf.writeFloat(pitch); buf.writeBoolean(true);
    }

    @Override public int translateBlockState(int raw) { return raw; }
}

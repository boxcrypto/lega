package net.lega.protocol.adapter.v1_18;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.18, 1.18.1, and 1.18.2
 * (Caves &amp; Cliffs: Part II).
 *
 * <h2>Major changes from 1.17</h2>
 * <ul>
 *   <li><b>World height</b>: Y range expanded from 0–255 to -64–320
 *       (total height 384 blocks).</li>
 *   <li><b>Chunk format overhaul</b>: Chunk sections now use a palette-based
 *       system with direct/indirect modes; each section stores biomes too.</li>
 *   <li>Improved cave generation: dripstone caves, lush caves, deep dark.</li>
 *   <li>New biomes: grove, jagged peaks, frozen peaks, stony peaks,
 *       snowy slopes, meadow, swamp improvement.</li>
 *   <li>Spawn entity packet unchanged from 1.17.</li>
 *   <li>1.18.2: Server list ping now includes enforced secure chat boolean.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_18 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x0F;
    private static final int PKT_ACTION_BAR          = 0x41;
    private static final int PKT_CHUNK_DATA          = 0x22;
    private static final int PKT_ENTITY_POSITION     = 0x2A;
    private static final int PKT_TAB_LIST            = 0x5F;
    private static final int PKT_BOSS_BAR            = 0x0D;
    private static final int PKT_TITLE_TEXT          = 0x59;
    private static final int PKT_TITLE_SUBTITLE      = 0x57;
    private static final int PKT_TITLE_TIMES         = 0x5A;
    private static final int PKT_DISCONNECT          = 0x1A;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x4D;

    private static final int PKT_SB_CHAT             = 0x03;
    private static final int PKT_SB_POSITION         = 0x12;

    /** Minimum Y coordinate in the 1.18 world height. */
    public static final int MIN_Y = -64;
    /** Maximum Y coordinate in the 1.18 world height. */
    public static final int MAX_Y = 320;
    /** Total world height in 1.18. */
    public static final int WORLD_HEIGHT = MAX_Y - MIN_Y;

    public Adapter_1_18() {
        super(
            MinecraftVersion.V1_18,
            MinecraftVersion.V1_18_1,
            MinecraftVersion.V1_18_2
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_18_2; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()            { return true; }
    @Override public boolean supportsActionBar()       { return true; }
    @Override public boolean supportsBossBar()         { return true; }
    @Override public boolean supportsTitles()          { return true; }
    @Override public boolean supportsOffHand()         { return true; }
    @Override public boolean supportsFlattening()      { return true; }
    @Override public boolean supportsChatComponents()  { return true; }
    @Override public boolean supportsHexColour()       { return true; }
    @Override public boolean supportsNewChunkFormat()  { return true; }   // KEY: -64 to 320
    @Override public boolean supportsServerIcon()      { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_CHAT_MESSAGE; }
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

    @Override
    public byte[] encodeChat(String message, int position) {
        if (position == 2) return encodeActionBar(message);
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_CHAT_MESSAGE);
            writeString(buf, toJson(message));
            buf.writeByte(position & 0xFF);
            buf.writeLong(0L); buf.writeLong(0L); // sender UUID
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    public byte[] encodeActionBar(String message) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_ACTION_BAR);
            writeString(buf, toJson(message));
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
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

    /**
     * Returns the number of chunk sections for the 1.18 world height.
     * (384 / 16 = 24 sections)
     */
    public int chunkSectionCount() {
        return WORLD_HEIGHT / 16;
    }

    /**
     * Converts a block Y coordinate to the chunk section index in 1.18.
     *
     * @param blockY absolute Y coordinate
     * @return section index (0 = bottommost, 23 = topmost)
     */
    public int yToSectionIndex(int blockY) {
        return (blockY - MIN_Y) >> 4;
    }
}

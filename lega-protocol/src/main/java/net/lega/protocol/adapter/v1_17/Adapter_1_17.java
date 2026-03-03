package net.lega.protocol.adapter.v1_17;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.17 and 1.17.1 (Caves &amp; Cliffs: Part I).
 *
 * <h2>Major changes from 1.16</h2>
 * <ul>
 *   <li>Chunk height still 256 blocks (Part I — full height change is in 1.18).</li>
 *   <li>Amethyst geodes, spyglass, lightning rod, copper, deepslate added.</li>
 *   <li>Axolotl, goat, glow squid added.</li>
 *   <li>Spawn entity and remove entities packets changed:
 *       <ul>
 *         <li>Entity velocity now sent on spawn.</li>
 *         <li>Remove entities uses VarInt array (was single short in 1.16).</li>
 *       </ul>
 *   </li>
 *   <li>Title packets split from one dispatch packet into separate packets:
 *       SET_TITLE (0x59), SET_SUBTITLE (0x57), SET_TIMES (0x5A), CLEAR (0x10).</li>
 *   <li>Action bar moved to its own dedicated packet (0x41).</li>
 *   <li>Chunk data no longer includes sky light — separate 0x26 packet.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_17 extends AbstractVersionAdapter {

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
    private static final int PKT_TITLE_CLEAR         = 0x10;
    private static final int PKT_DISCONNECT          = 0x1A;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x4D;

    private static final int PKT_SB_CHAT             = 0x03;
    private static final int PKT_SB_POSITION         = 0x12;

    public Adapter_1_17() {
        super(MinecraftVersion.V1_17, MinecraftVersion.V1_17_1);
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_17_1; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()            { return true; }
    @Override public boolean supportsActionBar()       { return true; }
    @Override public boolean supportsBossBar()         { return true; }
    @Override public boolean supportsTitles()          { return true; }
    @Override public boolean supportsOffHand()         { return true; }
    @Override public boolean supportsFlattening()      { return true; }
    @Override public boolean supportsChatComponents()  { return true; }
    @Override public boolean supportsHexColour()       { return true; }
    @Override public boolean supportsServerIcon()      { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_CHAT_MESSAGE; }
    @Override public int packetIdChunkData()             { return PKT_CHUNK_DATA; }
    @Override public int packetIdEntityPosition()        { return PKT_ENTITY_POSITION; }
    @Override public int packetIdTabListHeaderFooter()   { return PKT_TAB_LIST; }
    @Override public int packetIdBossBar()               { return PKT_BOSS_BAR; }
    @Override public int packetIdActionBar()             { return PKT_ACTION_BAR; }
    /** Title text uses its own dedicated packet in 1.17+. */
    @Override public int packetIdTitle()                 { return PKT_TITLE_TEXT; }
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
            // 1.17 adds sender UUID for chat positioning
            buf.writeLong(0L);  // UUID MSB (system = 0)
            buf.writeLong(0L);  // UUID LSB
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * Sends an action bar message via the dedicated 0x41 packet.
     */
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

    /**
     * 1.17: title uses THREE separate packets (times, title, subtitle).
     */
    @Override
    public byte[] encodeTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        ByteBuf buf = Unpooled.buffer();
        try {
            // Times
            writeVarInt(buf, PKT_TITLE_TIMES);
            buf.writeInt(fadeIn); buf.writeInt(stay); buf.writeInt(fadeOut);
            // Title
            if (title != null) {
                writeVarInt(buf, PKT_TITLE_TEXT);
                writeString(buf, toJson(title));
            }
            // Subtitle
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
}

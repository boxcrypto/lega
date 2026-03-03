package net.lega.protocol.adapter.v1_13;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.13, 1.13.1, and 1.13.2 (Update Aquatic).
 *
 * <h2>Major changes from 1.12</h2>
 * <ul>
 *   <li><b>Block Flattening</b>: Block IDs + metadata merged into a single
 *       block-state integer. All numeric block IDs are gone.</li>
 *   <li><b>Identifier system</b>: All block/item/entity/biome identifiers are
 *       now namespaced strings (e.g. {@code "minecraft:stone"}).</li>
 *   <li>Chat components now support {@code "keybind"} and {@code "nbt"} types.</li>
 *   <li>Swimming animation (Sprint + Sneak in water) introduced.</li>
 *   <li>Dolphins, drowned, phantom, trident, turtles, conduit added.</li>
 *   <li>Chunk format changed: each section uses 14-bit packed block states.</li>
 *   <li>Server-side tab-complete with full packet rework (0x0E → 0x10).</li>
 *   <li>Data packs introduced for world generation customisation.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_13 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x0E;
    private static final int PKT_CHUNK_DATA          = 0x22;
    private static final int PKT_ENTITY_POSITION     = 0x28;
    private static final int PKT_TAB_LIST            = 0x4E;
    private static final int PKT_BOSS_BAR            = 0x0C;
    private static final int PKT_TITLE               = 0x4B;
    private static final int PKT_DISCONNECT          = 0x1B;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x3E;
    private static final int PKT_DECLARE_COMMANDS    = 0x11;
    private static final int PKT_TAB_COMPLETE        = 0x10;

    private static final int PKT_SB_CHAT             = 0x02;
    private static final int PKT_SB_POSITION         = 0x10;

    public Adapter_1_13() {
        super(
            MinecraftVersion.V1_13,
            MinecraftVersion.V1_13_1,
            MinecraftVersion.V1_13_2
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_13_2; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()            { return true; }
    @Override public boolean supportsActionBar()       { return true; }
    @Override public boolean supportsBossBar()         { return true; }
    @Override public boolean supportsTitles()          { return true; }
    @Override public boolean supportsOffHand()         { return true; }
    @Override public boolean supportsFlattening()      { return true; }   // KEY change
    @Override public boolean supportsChatComponents()  { return true; }
    @Override public boolean supportsServerIcon()      { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_CHAT_MESSAGE; }
    @Override public int packetIdChunkData()             { return PKT_CHUNK_DATA; }
    @Override public int packetIdEntityPosition()        { return PKT_ENTITY_POSITION; }
    @Override public int packetIdTabListHeaderFooter()   { return PKT_TAB_LIST; }
    @Override public int packetIdBossBar()               { return PKT_BOSS_BAR; }
    @Override public int packetIdTitle()                 { return PKT_TITLE; }
    @Override public int packetIdDisconnect()            { return PKT_DISCONNECT; }
    @Override public int packetIdSpawnEntity()           { return PKT_SPAWN_ENTITY; }
    @Override public int packetIdEntityMetadata()        { return PKT_ENTITY_METADATA; }
    @Override public int packetIdServerboundChat()       { return PKT_SB_CHAT; }
    @Override public int packetIdServerboundPosition()   { return PKT_SB_POSITION; }

    // ── encoding ──────────────────────────────────────────────────────────

    @Override
    public byte[] encodeChat(String message, int position) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_CHAT_MESSAGE);
            writeString(buf, toJson(message));
            buf.writeByte(position & 0xFF);
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
            writeVarInt(buf, PKT_TITLE); writeVarInt(buf, 2);
            buf.writeInt(fadeIn); buf.writeInt(stay); buf.writeInt(fadeOut);
            if (title != null) {
                writeVarInt(buf, PKT_TITLE); writeVarInt(buf, 0);
                writeString(buf, toJson(title));
            }
            if (subtitle != null) {
                writeVarInt(buf, PKT_TITLE); writeVarInt(buf, 1);
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
        float yaw = buf.readFloat(); float pitch = buf.readFloat();
        buf.readBoolean();
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(yaw); buf.writeFloat(pitch); buf.writeBoolean(true);
    }

    /**
     * In 1.13, block states are already the flat integer — no translation needed.
     */
    @Override
    public int translateBlockState(int raw) { return raw; }
}

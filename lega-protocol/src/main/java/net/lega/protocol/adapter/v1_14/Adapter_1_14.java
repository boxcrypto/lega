package net.lega.protocol.adapter.v1_14;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.14, 1.14.1, 1.14.2, 1.14.3, and 1.14.4
 * (Village &amp; Pillage).
 *
 * <h2>Changes from 1.13</h2>
 * <ul>
 *   <li>Villager AI completely rewritten — trade rebalancing.</li>
 *   <li>Pillagers, ravagers, cats (split from ocelots), fox, wandering trader added.</li>
 *   <li>Scaffolding, barrel, smoker, blast furnace, composter, lantern added.</li>
 *   <li>Chunk light data separated into a dedicated packet (0x25).</li>
 *   <li>Entity metadata index for pose field introduced (index 6).</li>
 *   <li>Tags packet (0x5B) added for dynamic block/item/fluid tag synchronisation.</li>
 *   <li>Declare recipes packet updated with new recipe types.</li>
 *   <li>Chunk data format: bit mask uses direct non-empty section masks.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_14 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x0E;
    private static final int PKT_CHUNK_DATA          = 0x21;
    private static final int PKT_UPDATE_LIGHT        = 0x25;
    private static final int PKT_ENTITY_POSITION     = 0x27;
    private static final int PKT_TAB_LIST            = 0x53;
    private static final int PKT_BOSS_BAR            = 0x0C;
    private static final int PKT_TITLE               = 0x4F;
    private static final int PKT_DISCONNECT          = 0x1A;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x44;
    private static final int PKT_TAGS                = 0x5B;

    private static final int PKT_SB_CHAT             = 0x03;
    private static final int PKT_SB_POSITION         = 0x11;

    public Adapter_1_14() {
        super(
            MinecraftVersion.V1_14,
            MinecraftVersion.V1_14_1,
            MinecraftVersion.V1_14_2,
            MinecraftVersion.V1_14_3,
            MinecraftVersion.V1_14_4
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_14_4; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()            { return true; }
    @Override public boolean supportsActionBar()       { return true; }
    @Override public boolean supportsBossBar()         { return true; }
    @Override public boolean supportsTitles()          { return true; }
    @Override public boolean supportsOffHand()         { return true; }
    @Override public boolean supportsFlattening()      { return true; }
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

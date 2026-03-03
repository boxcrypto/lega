package net.lega.protocol.adapter.v1_15;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.15, 1.15.1, and 1.15.2 (Buzzy Bees).
 *
 * <h2>Changes from 1.14</h2>
 * <ul>
 *   <li>Bees, bee nests, honeycomb, honey bottle, honey block added.</li>
 *   <li>Minor performance improvements in chunk lighting (BugFix update).</li>
 *   <li>Entity ID for bee added (0x73).</li>
 *   <li>Bug fix release — no major protocol changes.</li>
 *   <li>Spawn entity packet now includes VarInt entity type (was short in older).</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_15 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x0E;
    private static final int PKT_CHUNK_DATA          = 0x22;
    private static final int PKT_ENTITY_POSITION     = 0x27;
    private static final int PKT_TAB_LIST            = 0x53;
    private static final int PKT_BOSS_BAR            = 0x0C;
    private static final int PKT_TITLE               = 0x4F;
    private static final int PKT_DISCONNECT          = 0x1A;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x44;

    private static final int PKT_SB_CHAT             = 0x03;
    private static final int PKT_SB_POSITION         = 0x11;

    public Adapter_1_15() {
        super(
            MinecraftVersion.V1_15,
            MinecraftVersion.V1_15_1,
            MinecraftVersion.V1_15_2
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_15_2; }

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

package net.lega.protocol.adapter.v1_9;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.9, 1.9.2, and 1.9.4 (Combat Update).
 *
 * <h2>Changes from 1.8</h2>
 * <ul>
 *   <li>Off-hand slot introduced (dual-wield).</li>
 *   <li>Attack cooldown mechanic added.</li>
 *   <li>Boss-bar is now native (Ender Dragon no longer needs NPC hack).</li>
 *   <li>Stance field removed from player position packets.</li>
 *   <li>Title actions merged into a single unified Title packet.</li>
 *   <li>Chunk data format: biome data per section, not global.</li>
 *   <li>Elytra, end cities, shulker boxes, and end gateways introduced.</li>
 *   <li>Packet IDs renumbered significantly.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public class Adapter_1_9 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE       = 0x0F;
    private static final int PKT_CHUNK_DATA         = 0x20;
    private static final int PKT_ENTITY_POSITION    = 0x25;
    private static final int PKT_TAB_LIST           = 0x48;
    private static final int PKT_BOSS_BAR           = 0x0C;
    private static final int PKT_TITLE              = 0x45;
    private static final int PKT_ACTION_BAR         = 0x0F; // position=2
    private static final int PKT_DISCONNECT         = 0x1A;
    private static final int PKT_SPAWN_ENTITY       = 0x00;
    private static final int PKT_ENTITY_METADATA    = 0x39;

    private static final int PKT_SB_CHAT            = 0x02;
    private static final int PKT_SB_POSITION        = 0x0C;

    public Adapter_1_9() {
        super(MinecraftVersion.V1_9, MinecraftVersion.V1_9_2, MinecraftVersion.V1_9_4);
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_9_4; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()        { return true; }
    @Override public boolean supportsActionBar()   { return true; }
    @Override public boolean supportsBossBar()     { return true; }
    @Override public boolean supportsTitles()      { return true; }
    @Override public boolean supportsOffHand()     { return true; }
    @Override public boolean supportsServerIcon()  { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_CHAT_MESSAGE; }
    @Override public int packetIdChunkData()             { return PKT_CHUNK_DATA; }
    @Override public int packetIdEntityPosition()        { return PKT_ENTITY_POSITION; }
    @Override public int packetIdTabListHeaderFooter()   { return PKT_TAB_LIST; }
    @Override public int packetIdBossBar()               { return PKT_BOSS_BAR; }
    @Override public int packetIdTitle()                 { return PKT_TITLE; }
    @Override public int packetIdActionBar()             { return PKT_ACTION_BAR; }
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
            // Times
            writeVarInt(buf, PKT_TITLE);
            writeVarInt(buf, 2);
            buf.writeInt(fadeIn);
            buf.writeInt(stay);
            buf.writeInt(fadeOut);
            // Title
            if (title != null) {
                writeVarInt(buf, PKT_TITLE);
                writeVarInt(buf, 0);
                writeString(buf, toJson(title));
            }
            // Subtitle
            if (subtitle != null) {
                writeVarInt(buf, PKT_TITLE);
                writeVarInt(buf, 1);
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
     * 1.9+ drops the stance field entirely.
     */
    @Override
    public double[] readPosition(ByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        buf.readBoolean(); // onGround
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeBoolean(true);
    }
}

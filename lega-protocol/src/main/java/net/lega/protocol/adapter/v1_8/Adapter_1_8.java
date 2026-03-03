package net.lega.protocol.adapter.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.8 and 1.8.9.
 *
 * <h2>Changes from 1.7</h2>
 * <ul>
 *   <li>UUID added to all entity spawn packets.</li>
 *   <li>Chat is now JSON text components (MiniMessage-subset).</li>
 *   <li>Action-bar chat position (position=2) is now supported.</li>
 *   <li>Tab-list header / footer packet added (0x48).</li>
 *   <li>Player skins are 64×64 (full texture).</li>
 *   <li>Name-tag visibility is now a per-team setting.</li>
 *   <li>Scoreboard: display-slot packet format updated.</li>
 *   <li>Titles: NOT yet in 1.8 vanilla — introduced in 1.8 snapshots, full in 1.9.</li>
 *   <li>Resource packs: now forced-send via 0x48.</li>
 *   <li>Barrier block and Spectator mode introduced.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_8 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x02;
    private static final int PKT_CHUNK_DATA          = 0x21;
    private static final int PKT_ENTITY_POSITION     = 0x08;
    private static final int PKT_TAB_LIST            = 0x48;
    private static final int PKT_DISCONNECT          = 0x40;
    private static final int PKT_SPAWN_ENTITY        = 0x0C;
    private static final int PKT_ENTITY_METADATA     = 0x1C;
    private static final int PKT_SPAWN_MOB           = 0x0F;
    private static final int PKT_TITLE               = 0x45; // added in 1.8.3+
    private static final int PKT_ACTION_BAR          = 0x02; // position=2 variant

    private static final int PKT_SB_CHAT             = 0x01;
    private static final int PKT_SB_POSITION         = 0x04;

    public Adapter_1_8() {
        super(MinecraftVersion.V1_8, MinecraftVersion.V1_8_9);
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_8_9; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()        { return true; }
    @Override public boolean supportsActionBar()   { return true; }
    @Override public boolean supportsTitles()      { return true; }  // 1.8.3+
    @Override public boolean supportsServerIcon()  { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_CHAT_MESSAGE; }
    @Override public int packetIdChunkData()             { return PKT_CHUNK_DATA; }
    @Override public int packetIdEntityPosition()        { return PKT_ENTITY_POSITION; }
    @Override public int packetIdTabListHeaderFooter()   { return PKT_TAB_LIST; }
    @Override public int packetIdDisconnect()            { return PKT_DISCONNECT; }
    @Override public int packetIdSpawnEntity()           { return PKT_SPAWN_ENTITY; }
    @Override public int packetIdEntityMetadata()        { return PKT_ENTITY_METADATA; }
    @Override public int packetIdTitle()                 { return PKT_TITLE; }
    @Override public int packetIdActionBar()             { return PKT_ACTION_BAR; }
    @Override public int packetIdServerboundChat()       { return PKT_SB_CHAT; }
    @Override public int packetIdServerboundPosition()   { return PKT_SB_POSITION; }

    // ── encoding ──────────────────────────────────────────────────────────

    /**
     * 1.8 chat: JSON text component + 1 position byte.
     * Position 0 = chat, 1 = system, 2 = action bar.
     */
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

    /**
     * 1.8 titles use sub-action type bytes:
     * 0=title, 1=subtitle, 2=times, 3=clear, 4=reset
     */
    @Override
    public byte[] encodeTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        ByteBuf buf = Unpooled.buffer();
        try {
            // Times packet first
            writeVarInt(buf, PKT_TITLE);
            writeVarInt(buf, 2); // action: SET_TIMES
            buf.writeInt(fadeIn);
            buf.writeInt(stay);
            buf.writeInt(fadeOut);

            if (title != null) {
                writeVarInt(buf, PKT_TITLE);
                writeVarInt(buf, 0); // action: SET_TITLE
                writeString(buf, toJson(title));
            }
            if (subtitle != null) {
                writeVarInt(buf, PKT_TITLE);
                writeVarInt(buf, 1); // action: SET_SUBTITLE
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
     * 1.8 still includes the player "stance" field.
     */
    @Override
    public double[] readPosition(ByteBuf buf) {
        double x      = buf.readDouble();
        double y      = buf.readDouble();
        double stance = buf.readDouble();
        double z      = buf.readDouble();
        float  yaw    = buf.readFloat();
        float  pitch  = buf.readFloat();
        boolean onGround = buf.readBoolean();
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(y + 1.8);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeBoolean(true);
    }
}

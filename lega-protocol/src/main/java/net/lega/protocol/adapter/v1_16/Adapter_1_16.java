package net.lega.protocol.adapter.v1_16;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.16 through 1.16.5 (Nether Update).
 *
 * <h2>Major changes from 1.15</h2>
 * <ul>
 *   <li><b>Hex colour in chat</b>: {@code {"color":"#RRGGBB"}} now valid in
 *       text components. Older clients receive fallback §-codes.</li>
 *   <li>Crimson/warped forests, soul sand valley, basalt delta biomes added.</li>
 *   <li>Nether dimension increased to full height (0–255 → still capped, but
 *       Nether ceiling removed by gamerule).</li>
 *   <li>Piglins, hoglins, zoglins, striders added.</li>
 *   <li>Ancient Debris and Netherite gear added.</li>
 *   <li>Dimension registry moved to codec NBT (Join Game packet).</li>
 *   <li>Respawn anchor and lodestone added.</li>
 *   <li>1.16.2: Piglin brutes, bastion remnants.</li>
 *   <li>1.16.3: Dimension codec format updated (codecs in NBT).</li>
 *   <li>1.16.4/5: Bundles removed from experimental (still not in stable).</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_16 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x0E;
    private static final int PKT_CHUNK_DATA          = 0x22;
    private static final int PKT_ENTITY_POSITION     = 0x28;
    private static final int PKT_TAB_LIST            = 0x53;
    private static final int PKT_BOSS_BAR            = 0x0C;
    private static final int PKT_TITLE               = 0x4F;
    private static final int PKT_DISCONNECT          = 0x19;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x44;
    private static final int PKT_JOIN_GAME           = 0x26;   // now includes dimension codec

    private static final int PKT_SB_CHAT             = 0x03;
    private static final int PKT_SB_POSITION         = 0x12;

    public Adapter_1_16() {
        super(
            MinecraftVersion.V1_16,
            MinecraftVersion.V1_16_1,
            MinecraftVersion.V1_16_2,
            MinecraftVersion.V1_16_3,
            MinecraftVersion.V1_16_4,
            MinecraftVersion.V1_16_5
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_16_5; }

    // ── features ──────────────────────────────────────────────────────────

    @Override public boolean supportsUUID()            { return true; }
    @Override public boolean supportsActionBar()       { return true; }
    @Override public boolean supportsBossBar()         { return true; }
    @Override public boolean supportsTitles()          { return true; }
    @Override public boolean supportsOffHand()         { return true; }
    @Override public boolean supportsFlattening()      { return true; }
    @Override public boolean supportsChatComponents()  { return true; }
    @Override public boolean supportsHexColour()       { return true; }  // KEY: hex colours
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

    /**
     * Build a JSON chat component that carries the hex colour {@code colour}
     * and the raw {@code message} as {@code text}.
     *
     * @param message plain message text
     * @param colour  CSS hex colour string e.g. {@code "#FF5733"}, or null
     * @param position 0=chat,1=system,2=action bar
     */
    public byte[] encodeChatHex(String message, String colour, int position) {
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = colour != null
            ? "{\"text\":\"" + escaped + "\",\"color\":\"" + colour + "\"}"
            : "{\"text\":\"" + escaped + "\"}";

        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_CHAT_MESSAGE);
            writeString(buf, json);
            buf.writeByte(position & 0xFF);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    @Override
    public byte[] encodeChat(String message, int position) {
        return encodeChatHex(message, null, position);
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

package net.lega.protocol.adapter.v1_20;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.20 through 1.20.6 (Trails &amp; Tales).
 *
 * <h2>Major changes from 1.19</h2>
 * <ul>
 *   <li>Archaeology system: brush, suspicious sand/gravel, sherds.</li>
 *   <li>Camel, sniffer, armadillo added.</li>
 *   <li>Bamboo wood set, cherry blossom biome added.</li>
 *   <li>Decorated pots, hanging signs, chiseled bookshelves added.</li>
 *   <li>Trial chambers structure (1.20.3).</li>
 *   <li>Breeze mob, wind charge item, vault block (1.20.3).</li>
 *   <li><b>1.20.5</b>: Item stack component system — items now use NBT
 *       component map instead of flat tag. {@link #supportsItemComponents()} = true.</li>
 *   <li>1.20.5: {@code minecraft:item_components} registry sent in login.</li>
 *   <li>Configuration phase introduced between login and play (1.20.2).</li>
 *   <li>Entity event packet updated (ominous events).</li>
 *   <li>Bundle delivery confirmed stable (1.20.4).</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_20 extends AbstractVersionAdapter {

    // ── packet IDs (1.20.4 baseline, overridden where needed) ────────────

    private static final int PKT_SYSTEM_CHAT         = 0x6C;
    private static final int PKT_PLAYER_CHAT         = 0x39;
    private static final int PKT_ACTION_BAR          = 0x4C;
    private static final int PKT_CHUNK_DATA          = 0x27;
    private static final int PKT_ENTITY_POSITION     = 0x2E;
    private static final int PKT_TAB_LIST            = 0x6A;
    private static final int PKT_BOSS_BAR            = 0x0A;
    private static final int PKT_TITLE_TEXT          = 0x61;
    private static final int PKT_TITLE_SUBTITLE      = 0x60;
    private static final int PKT_TITLE_TIMES         = 0x62;
    private static final int PKT_DISCONNECT          = 0x1C;
    private static final int PKT_SPAWN_ENTITY        = 0x01;
    private static final int PKT_ENTITY_METADATA     = 0x52;
    private static final int PKT_BUNDLE_DELIMITER    = 0x00;

    private static final int PKT_SB_CHAT             = 0x05;
    private static final int PKT_SB_CHAT_COMMAND     = 0x04;
    private static final int PKT_SB_POSITION         = 0x17;

    public Adapter_1_20() {
        super(
            MinecraftVersion.V1_20,
            MinecraftVersion.V1_20_1,
            MinecraftVersion.V1_20_2,
            MinecraftVersion.V1_20_3,
            MinecraftVersion.V1_20_4,
            MinecraftVersion.V1_20_5,
            MinecraftVersion.V1_20_6
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_20_6; }

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
    @Override public boolean supportsChatSigning()      { return true; }
    @Override public boolean supportsSecureChatHello()  { return true; }
    @Override public boolean supportsBundle()           { return true; }
    @Override public boolean supportsItemComponents()   { return true; }   // 1.20.5+
    @Override public boolean supportsServerIcon()       { return true; }

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_SYSTEM_CHAT; }
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
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_SYSTEM_CHAT);
            writeString(buf, toJson(message));
            buf.writeBoolean(position == 2); // overlay?
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

    /**
     * 1.20.5+ item stack encoding uses a component map.
     * Returns a minimal component-encoded item for a given item ID and count.
     *
     * @param itemId    namespaced item ID, e.g. {@code "minecraft:diamond_sword"}
     * @param count     stack size
     * @param customName optional display name JSON, may be null
     * @return raw payload bytes for the item stack field
     */
    public byte[] encodeItemStack(String itemId, int count, String customName) {
        ByteBuf buf = Unpooled.buffer();
        try {
            // Item presence flag
            buf.writeBoolean(true);
            // Item ID as VarInt (client resolves from registry) — simplified: write string
            writeString(buf, itemId);
            // Count as VarInt
            writeVarInt(buf, count);
            // Component add count
            writeVarInt(buf, customName != null ? 1 : 0);
            if (customName != null) {
                // Component type: minecraft:custom_name
                writeString(buf, "minecraft:custom_name");
                writeString(buf, customName);
            }
            // Component remove count
            writeVarInt(buf, 0);
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

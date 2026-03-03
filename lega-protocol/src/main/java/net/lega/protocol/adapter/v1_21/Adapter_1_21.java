package net.lega.protocol.adapter.v1_21;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.21, 1.21.1, 1.21.2, 1.21.3, and 1.21.4
 * (Tricky Trials).
 *
 * <h2>Major changes from 1.20</h2>
 * <ul>
 *   <li>Trial chambers fully released with vault blocks and ominous trials.</li>
 *   <li>Breeze mob, wind charge projectile, mace weapon introduced.</li>
 *   <li>Heavy core block (mace crafting), ominous bottle, ominous banner.</li>
 *   <li>Armadillo scute, wolf armour added.</li>
 *   <li>1.21.2: Creaking, pale oak wood set, pale garden biome.</li>
 *   <li>1.21.4: Bundles officially released (no longer experimental).</li>
 *   <li>Packet IDs further renumbered from 1.20.6 baseline.</li>
 *   <li>New entity events for wind charge and breeze animations.</li>
 *   <li>Login sequence: configuration phase more strictly enforced.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_21 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_SYSTEM_CHAT         = 0x70;
    private static final int PKT_PLAYER_CHAT         = 0x3C;
    private static final int PKT_ACTION_BAR          = 0x4F;
    private static final int PKT_CHUNK_DATA          = 0x27;
    private static final int PKT_ENTITY_POSITION     = 0x2F;
    private static final int PKT_TAB_LIST            = 0x6E;
    private static final int PKT_BOSS_BAR            = 0x0A;
    private static final int PKT_TITLE_TEXT          = 0x65;
    private static final int PKT_TITLE_SUBTITLE      = 0x64;
    private static final int PKT_TITLE_TIMES         = 0x66;
    private static final int PKT_DISCONNECT          = 0x1D;
    private static final int PKT_SPAWN_ENTITY        = 0x01;
    private static final int PKT_ENTITY_METADATA     = 0x54;
    private static final int PKT_BUNDLE_DELIMITER    = 0x00;
    private static final int PKT_WIND_CHARGE_FIRE    = 0x7E;  // new in 1.21
    private static final int PKT_ENTITY_EVENT        = 0x35;

    private static final int PKT_SB_CHAT             = 0x06;
    private static final int PKT_SB_CHAT_COMMAND     = 0x05;
    private static final int PKT_SB_POSITION         = 0x1A;
    private static final int PKT_SB_SWING_ARM        = 0x38;
    private static final int PKT_SB_USE_ITEM         = 0x3E;

    public Adapter_1_21() {
        super(
            MinecraftVersion.V1_21,
            MinecraftVersion.V1_21_1,
            MinecraftVersion.V1_21_2,
            MinecraftVersion.V1_21_3,
            MinecraftVersion.V1_21_4
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_21_4; }

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
    @Override public boolean supportsBundle()           { return true; }   // officially released 1.21.4
    @Override public boolean supportsItemComponents()   { return true; }
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
            buf.writeBoolean(position == 2);
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
     * Encodes an entity event packet for a specific entity.
     *
     * @param entityId  numeric entity ID
     * @param eventId   event byte (e.g., 60 = wind charge burst, 62 = breeze idle)
     */
    public byte[] encodeEntityEvent(int entityId, byte eventId) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_ENTITY_EVENT);
            buf.writeInt(entityId);
            buf.writeByte(eventId);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * Encodes a bundle delimiter packet.
     * Surrounding packets with bundle delimiters tells the client
     * to process them atomically in the same game tick.
     */
    public byte[] encodeBundleDelimiter() {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_BUNDLE_DELIMITER);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * Encodes an item stack using the 1.21 component format.
     *
     * @param itemId       namespaced item ID
     * @param count        stack size (1–99 supported by the minecraft protocol)
     * @param customName   JSON text component for the item name, or null
     * @param enchantments map of enchantment ID → level, or null
     */
    public byte[] encodeItemStack(String itemId, int count, String customName,
                                  java.util.Map<String, Integer> enchantments) {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeBoolean(true); // item present
            writeString(buf, itemId);
            writeVarInt(buf, count);

            int componentCount = 0;
            if (customName != null) componentCount++;
            if (enchantments != null && !enchantments.isEmpty()) componentCount++;

            writeVarInt(buf, componentCount);

            if (customName != null) {
                writeString(buf, "minecraft:custom_name");
                writeString(buf, customName);
            }
            if (enchantments != null && !enchantments.isEmpty()) {
                writeString(buf, "minecraft:enchantments");
                writeVarInt(buf, enchantments.size());
                for (var entry : enchantments.entrySet()) {
                    writeString(buf, entry.getKey());
                    writeVarInt(buf, entry.getValue());
                }
            }

            writeVarInt(buf, 0); // removed components

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

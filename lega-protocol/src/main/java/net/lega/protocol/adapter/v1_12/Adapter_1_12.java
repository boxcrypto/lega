package net.lega.protocol.adapter.v1_12;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

/**
 * Protocol adapter for Minecraft 1.12, 1.12.1, and 1.12.2 (World of Color Update).
 *
 * <h2>Changes from 1.11</h2>
 * <ul>
 *   <li>Advancements system added — dedicated packet 0x4D.</li>
 *   <li>Recipes system added — recipe book, unlock recipes packet 0x30.</li>
 *   <li>Concrete, terracotta, glazed terracotta blocks added.</li>
 *   <li>Parrots and illusioner illager introduced.</li>
 *   <li>Chat component {@code "insertion"} field added for shift-click.</li>
 *   <li>1.12.1: Server-side chat delay to prevent rapid token use.</li>
 *   <li>1.12.2: Minor protocol tweak, enchanting table packet changed.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_12 extends AbstractVersionAdapter {

    // ── packet IDs ────────────────────────────────────────────────────────

    private static final int PKT_CHAT_MESSAGE        = 0x0F;
    private static final int PKT_CHUNK_DATA          = 0x20;
    private static final int PKT_ENTITY_POSITION     = 0x25;
    private static final int PKT_TAB_LIST            = 0x49;
    private static final int PKT_BOSS_BAR            = 0x0C;
    private static final int PKT_TITLE               = 0x4B;
    private static final int PKT_DISCONNECT          = 0x1A;
    private static final int PKT_SPAWN_ENTITY        = 0x00;
    private static final int PKT_ENTITY_METADATA     = 0x3C;
    private static final int PKT_ADVANCEMENTS        = 0x4D;
    private static final int PKT_UNLOCK_RECIPES      = 0x30;

    private static final int PKT_SB_CHAT             = 0x02;
    private static final int PKT_SB_POSITION         = 0x0E;

    public Adapter_1_12() {
        super(
            MinecraftVersion.V1_12,
            MinecraftVersion.V1_12_1,
            MinecraftVersion.V1_12_2
        );
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_12_2; }

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
        double x     = buf.readDouble();
        double y     = buf.readDouble();
        double z     = buf.readDouble();
        float  yaw   = buf.readFloat();
        float  pitch = buf.readFloat();
        buf.readBoolean();
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(yaw); buf.writeFloat(pitch);
        buf.writeBoolean(true);
    }

    // ── 1.12-specific: advancements ───────────────────────────────────────

    /**
     * Encodes an "advancement granted" notification for a single advancement key.
     *
     * @param advKey  namespaced advancement key, e.g. "story/root"
     * @param reset   if true, clears all previous advancements first
     */
    public byte[] encodeAdvancementGrant(String advKey, boolean reset) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_ADVANCEMENTS);
            buf.writeBoolean(reset);
            writeVarInt(buf, 1);         // added count
            writeString(buf, "minecraft:" + advKey);
            // Empty criteria map and display for simplicity
            writeVarInt(buf, 0);         // removed count
            writeVarInt(buf, 0);         // progress entries
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }
}

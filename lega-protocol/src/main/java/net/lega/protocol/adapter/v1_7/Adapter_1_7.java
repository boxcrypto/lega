package net.lega.protocol.adapter.v1_7;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.AbstractVersionAdapter;

import java.nio.charset.StandardCharsets;

/**
 * Protocol adapter for Minecraft 1.7.2 and 1.7.10.
 *
 * <h2>Key characteristics</h2>
 * <ul>
 *   <li>No UUIDs on the wire for entities — integer entity IDs only.</li>
 *   <li>String-based item IDs (numeric, e.g. {@code "1:0"}).</li>
 *   <li>Chat is plain text with legacy §-colour codes, limited to 100 chars.</li>
 *   <li>No tablist header/footer packets.</li>
 *   <li>No action bar, no titles, no bossbar.</li>
 *   <li>Chunk sections use 4096 block IDs + 2048 bytes of metadata.</li>
 *   <li>VarInt uses the same LEB-128 encoding as later versions.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_7 extends AbstractVersionAdapter {

    // ── packet IDs (clientbound / play state) ─────────────────────────────

    /** 0x02 — Chat message */
    private static final int PKT_CHAT_MESSAGE       = 0x02;
    /** 0x21 — Chunk data */
    private static final int PKT_CHUNK_DATA         = 0x21;
    /** 0x14 — Spawn named entity */
    private static final int PKT_SPAWN_ENTITY       = 0x14;
    /** 0x1C — Entity velocity */
    private static final int PKT_ENTITY_VELOCITY    = 0x1C;
    /** 0x1C — Entity metadata */
    private static final int PKT_ENTITY_METADATA    = 0x1D;
    /** 0x08 — Update player position */
    private static final int PKT_ENTITY_POSITION    = 0x08;
    /** 0x40 — Disconnect */
    private static final int PKT_DISCONNECT         = 0x40;

    /** Serverbound — 0x01 Chat, 0x04 Player position */
    private static final int PKT_SB_CHAT            = 0x01;
    private static final int PKT_SB_POSITION        = 0x04;

    // ─────────────────────────────────────────────────────────────────────

    public Adapter_1_7() {
        super(MinecraftVersion.V1_7_2, MinecraftVersion.V1_7_10);
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_7_10; }

    // ── feature flags ─────────────────────────────────────────────────────

    @Override public boolean supportsServerIcon() { return true; }  // introduced in 1.7

    // ── packet IDs ────────────────────────────────────────────────────────

    @Override public int packetIdChatMessage()           { return PKT_CHAT_MESSAGE; }
    @Override public int packetIdChunkData()             { return PKT_CHUNK_DATA; }
    @Override public int packetIdEntityPosition()        { return PKT_ENTITY_POSITION; }
    @Override public int packetIdDisconnect()            { return PKT_DISCONNECT; }
    @Override public int packetIdSpawnEntity()           { return PKT_SPAWN_ENTITY; }
    @Override public int packetIdEntityMetadata()        { return PKT_ENTITY_METADATA; }
    @Override public int packetIdServerboundChat()       { return PKT_SB_CHAT; }
    @Override public int packetIdServerboundPosition()   { return PKT_SB_POSITION; }

    // ── encoding ──────────────────────────────────────────────────────────

    /**
     * 1.7 chat: plain text limited to 100 chars.
     * Position 2 (action bar) is silently degraded to position 0.
     */
    @Override
    public byte[] encodeChat(String message, int position) {
        String trimmed = message.length() > 100 ? message.substring(0, 100) : message;
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_CHAT_MESSAGE);
            // 1.7: length-prefixed UTF-8 string (short prefix, max 32767)
            byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
            // No position byte in 1.7
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * 1.7 uses a short-length-prefixed string instead of VarInt.
     */
    @Override
    public int readVarInt(ByteBuf buf) {
        // Standard LEB-128 applies here too; 1.7 does use VarInt
        return super.readVarInt(buf);
    }

    @Override
    public double[] readPosition(ByteBuf buf) {
        // 1.7 uses absolute doubles for player position
        double x     = buf.readDouble();
        double y     = buf.readDouble();
        double stance = buf.readDouble(); // unique to 1.7/1.8
        double z     = buf.readDouble();
        float  yaw   = buf.readFloat();
        float  pitch = buf.readFloat();
        boolean onGround = buf.readBoolean();
        return new double[]{x, y, z, yaw, pitch}; // normalised output
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(y + 1.8);  // stance = y + player height
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeBoolean(true);    // onGround
    }

    /**
     * 1.7 does not support titles — returns empty array.
     */
    @Override
    public byte[] encodeTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        return new byte[0];
    }

    /**
     * 1.7 block states: upper 12 bits = block ID, lower 4 = metadata.
     * Maps to modern IDs via no-op (engine must handle the translation table).
     */
    @Override
    public int translateBlockState(int raw) {
        // (blockId << 4) | meta — no direct lookup available without full table
        return raw;
    }
}

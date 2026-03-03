package net.lega.protocol.adapter.v1_10;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.v1_9.Adapter_1_9;

/**
 * Protocol adapter for Minecraft 1.10 (Frostburn Update).
 *
 * <h2>Changes from 1.9</h2>
 * <ul>
 *   <li>Minor protocol revision — packet IDs are nearly identical to 1.9.4.</li>
 *   <li>Auto-jump introduced (client-side setting).</li>
 *   <li>Polar bears, husks, and strays added (new entity IDs).</li>
 *   <li>Zombie horses and skeleton horses are now rideable.</li>
 *   <li>Structure block introduced.</li>
 *   <li>Server resource pack enforcement improved.</li>
 * </ul>
 *
 * <p>Packet format is largely identical to 1.9.4; this adapter
 * delegates to {@link Adapter_1_9} for encoding and overrides
 * only what changed.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_10 extends Adapter_1_9 {

    // Auto-jump settings packet (client settings)
    private static final int PKT_CLIENT_SETTINGS  = 0x04;

    // 1.10 entity metadata format encodes slightly differently for zombies
    private static final int PKT_ENTITY_METADATA  = 0x39;

    public Adapter_1_10() {
        // Register only 1.10
        supported.clear();
        supported.add(MinecraftVersion.V1_10);
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_10; }

    /**
     * Encode a "change dimension" payload — format unchanged from 1.9.
     * Returns a tuple: [dimension int, difficulty byte, gamemode byte, levelType string].
     */
    public byte[] encodeRespawn(int dimension, int difficulty, int gameMode, String levelType) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, 0x33);   // Respawn packet ID
            buf.writeInt(dimension);
            buf.writeByte(difficulty);
            buf.writeByte(gameMode);
            writeString(buf, levelType);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }
}

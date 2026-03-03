package net.lega.protocol.adapter.v1_11;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.adapter.v1_9.Adapter_1_9;

/**
 * Protocol adapter for Minecraft 1.11 and 1.11.2 (Exploration Update).
 *
 * <h2>Changes from 1.10</h2>
 * <ul>
 *   <li>Shulker box inventory API extended.</li>
 *   <li>Llamas, vexes, elytra firework boost introduced.</li>
 *   <li>Observer block added.</li>
 *   <li>Woodland mansion, ocean monuments in stronghold loot.</li>
 *   <li>Totem of Undying item — new NBT structure.</li>
 *   <li>Chat now supports {@code translate} and {@code with} component keys.</li>
 *   <li>Entity ID constants shifted due to new mobs.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class Adapter_1_11 extends Adapter_1_9 {

    // Specific packets changed in 1.11
    private static final int PKT_CHAT_MESSAGE    = 0x0F;
    private static final int PKT_DISCONNECT      = 0x1A;
    private static final int PKT_CHUNK_DATA      = 0x20;

    public Adapter_1_11() {
        supported.clear();
        supported.add(MinecraftVersion.V1_11);
        supported.add(MinecraftVersion.V1_11_2);
    }

    @Override public MinecraftVersion primaryVersion() { return MinecraftVersion.V1_11_2; }

    @Override public int packetIdChatMessage()   { return PKT_CHAT_MESSAGE; }
    @Override public int packetIdDisconnect()    { return PKT_DISCONNECT; }
    @Override public int packetIdChunkData()     { return PKT_CHUNK_DATA; }

    /**
     * Encode a chat message with 1.11 translate-component support.
     *
     * @param translateKey  translation key (e.g. "chat.type.text"), or null for plain text
     * @param args          substitution arguments for the translate key
     * @param position      0=chat, 1=system, 2=action bar
     */
    public byte[] encodeChatTranslate(String translateKey, String[] args, int position) {
        StringBuilder json = new StringBuilder("{\"translate\":\"");
        json.append(translateKey).append("\"");
        if (args != null && args.length > 0) {
            json.append(",\"with\":[");
            for (int i = 0; i < args.length; i++) {
                json.append("{\"text\":\"").append(args[i].replace("\"", "\\\"")).append("\"}");
                if (i < args.length - 1) json.append(",");
            }
            json.append("]");
        }
        json.append("}");

        ByteBuf buf = Unpooled.buffer();
        try {
            writeVarInt(buf, PKT_CHAT_MESSAGE);
            writeString(buf, json.toString());
            buf.writeByte(position & 0xFF);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }
}

package net.lega.protocol.translation.impl;

/**
 * Translates chat/system-message packets between protocol versions.
 *
 * <h3>Version differences handled</h3>
 * <ul>
 *   <li><b>1.7.x → 1.8</b>: no change in structure; payload is a plain JSON string</li>
 *   <li><b>1.8–1.18 (CLIENTBOUND 0x0F / 0x02)</b>: [JSON String][position byte]</li>
 *   <li><b>1.19+</b>: chat split into system-chat (0x60 / 0x64) and player-chat (0x33),
 *       with optional Sender UUID field.  LEGA normalises older formats to system-chat
 *       when talking to ≥1.19 clients, and strips to the plain JSON string when sending
 *       to older clients.</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import io.netty.buffer.ByteBuf;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.translation.PacketTranslationContext;
import net.lega.protocol.translation.PacketTranslator;
import net.lega.protocol.translation.TranslationDirection;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ChatPacketTranslator implements PacketTranslator {

    // Protocol thresholds
    private static final int PROTOCOL_1_19 = MinecraftVersion.V1_19.getProtocolId();

    @Override
    public int sourcePacketId(PacketTranslationContext ctx) {
        // The source varies by direction
        if (ctx.direction() == TranslationDirection.CLIENTBOUND) {
            return ctx.serverAdapter().packetIdChatMessage();
        } else {
            return ctx.clientAdapter().packetIdServerboundChat();
        }
    }

    @Override
    public TranslationDirection direction() {
        // Handles both directions; we override appliesTo to filter
        return TranslationDirection.CLIENTBOUND;
    }

    @Override
    public boolean appliesTo(PacketTranslationContext ctx) {
        // Only needed when crossing the 1.19 chat-signing boundary
        int client = ctx.clientProtocol();
        int server = ctx.serverVersion().getProtocolId();
        boolean clientIs119Plus = client >= PROTOCOL_1_19;
        boolean serverIs119Plus = server >= PROTOCOL_1_19;
        return clientIs119Plus != serverIs119Plus; // boundary crossed
    }

    @Override
    public ByteBuf translate(ByteBuf in, PacketTranslationContext ctx) {
        int clientProto = ctx.clientProtocol();
        int serverProto = ctx.serverVersion().getProtocolId();

        boolean clientNeeds119Format = clientProto >= PROTOCOL_1_19;

        if (clientNeeds119Format && serverProto < PROTOCOL_1_19) {
            // Server emits old-style chat (JSON + position byte);
            // client expects 1.19+ system-chat format
            return convertOldToSystemChat(in, ctx);
        } else {
            // Server emits 1.19+ system-chat; old client expects JSON + position
            return convertSystemChatToOld(in, ctx);
        }
    }

    // ── up-conversion: old chat → 1.19+ system-chat ────────────────────────────

    /**
     * Old chat format: [JSON String(VarInt length + UTF-8)][position byte]
     * → 1.19 system-chat format: [JSON String][overlay boolean]
     */
    private ByteBuf convertOldToSystemChat(ByteBuf in, PacketTranslationContext ctx) {
        String json    = readString(in);
        int    posCode = in.isReadable() ? in.readByte() & 0xFF : 0;
        boolean overlay = posCode == 2; // position 2 = action bar

        ByteBuf out = in.alloc().buffer(json.length() + 4);
        writeString(out, json);
        out.writeBoolean(overlay);
        return out;
    }

    // ── down-conversion: 1.19+ system-chat → old chat ─────────────────────────

    /**
     * 1.19 system-chat: [JSON String][overlay boolean]
     * → old chat: [JSON String][position byte]
     */
    private ByteBuf convertSystemChatToOld(ByteBuf in, PacketTranslationContext ctx) {
        String  json    = readString(in);
        boolean overlay = in.isReadable() && in.readBoolean();

        ByteBuf out = in.alloc().buffer(json.length() + 5);
        writeString(out, json);
        out.writeByte(overlay ? 2 : 0); // 0=chat, 1=system, 2=action-bar
        return out;
    }

    // ── I/O helpers ────────────────────────────────────────────────────────────

    private static String readString(ByteBuf buf) {
        int len = readVarInt(buf);
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static int readVarInt(ByteBuf buf) {
        int result = 0, shift = 0;
        while (buf.isReadable()) {
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
        }
        return 0;
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}

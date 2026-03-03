package net.lega.protocol.translation.impl;

/**
 * Translates title and action-bar packets across the 1.17 protocol boundary.
 *
 * <h3>Protocol change at 1.17</h3>
 * <p>Before 1.17, a single <em>Title</em> packet (0x4F / 0x45 in various versions)
 * handled all title sub-commands via an {@code action} VarInt field:</p>
 * <pre>
 *  action=0 → set title text     (JSON string follows)
 *  action=1 → set subtitle text  (JSON string follows)
 *  action=2 → set action bar     (JSON string follows)
 *  action=3 → set timing         (fade-in:int, stay:int, fade-out:int)
 *  action=4 → hide title
 *  action=5 → reset title
 * </pre>
 *
 * <p>From 1.17 onward, these are separate packets:</p>
 * <ul>
 *   <li>0x59 – Set Title Text</li>
 *   <li>0x57 – Set Subtitle Text</li>
 *   <li>0x41 – Set Action Bar</li>
 *   <li>0x5A – Set Title Animation Times</li>
 *   <li>0x10 – Clear Titles (hide/reset flag)</li>
 * </ul>
 *
 * <p>This translator converts both ways: old single-packet ↔ new split packets.</p>
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

public final class TitlePacketTranslator implements PacketTranslator {

    private static final int PROTOCOL_1_17 = MinecraftVersion.V1_17.getProtocolId();

    // pre-1.17 unified Title packet action codes
    private static final int ACTION_TITLE     = 0;
    private static final int ACTION_SUBTITLE  = 1;
    private static final int ACTION_ACTIONBAR = 2;
    private static final int ACTION_TIMES     = 3;
    private static final int ACTION_HIDE      = 4;
    private static final int ACTION_RESET     = 5;

    @Override
    public int sourcePacketId(PacketTranslationContext ctx) {
        return ctx.serverAdapter().packetIdTitle();
    }

    @Override
    public TranslationDirection direction() {
        return TranslationDirection.CLIENTBOUND;
    }

    @Override
    public boolean appliesTo(PacketTranslationContext ctx) {
        if (ctx.direction() != TranslationDirection.CLIENTBOUND) return false;
        int cl = ctx.clientProtocol();
        int sv = ctx.serverVersion().getProtocolId();
        return (cl >= PROTOCOL_1_17) != (sv >= PROTOCOL_1_17);
    }

    @Override
    public ByteBuf translate(ByteBuf in, PacketTranslationContext ctx) {
        int clientProto = ctx.clientProtocol();
        int serverProto = ctx.serverVersion().getProtocolId();

        if (serverProto < PROTOCOL_1_17 && clientProto >= PROTOCOL_1_17) {
            // Old unified packet → new 1.17 split packet
            return convertUnifiedToSplit(in, ctx);
        } else {
            // New 1.17 split packet → old unified packet
            return convertSplitToUnified(in, ctx);
        }
    }

    // ── old → new ─────────────────────────────────────────────────────────────

    /**
     * Reads the pre-1.17 unified title packet and re-encodes as the appropriate
     * 1.17+ sub-packet.  The translation pipeline will re-prepend the correct
     * target packet ID via {@link net.lega.protocol.translation.PacketTranslationPipeline};
     * here we just encode the payload.
     */
    private ByteBuf convertUnifiedToSplit(ByteBuf in, PacketTranslationContext ctx) {
        int action = readVarInt(in);
        ByteBuf out = in.alloc().buffer(in.readableBytes() + 4);
        switch (action) {
            case ACTION_TITLE, ACTION_SUBTITLE, ACTION_ACTIONBAR -> {
                // Both old and new formats start with the JSON string
                String json = readString(in);
                writeString(out, json);
            }
            case ACTION_TIMES -> {
                // fade-in (int), stay (int), fade-out (int) — identical in 1.17
                out.writeInt(in.readInt());
                out.writeInt(in.readInt());
                out.writeInt(in.readInt());
            }
            case ACTION_HIDE, ACTION_RESET -> {
                // 1.17: Clear Titles packet has a boolean "reset" flag
                out.writeBoolean(action == ACTION_RESET);
            }
        }
        return out;
    }

    // ── new → old ─────────────────────────────────────────────────────────────

    /**
     * Converts a 1.17+ sub-packet back to the pre-1.17 unified title format.
     * We must identify <em>which</em> 1.17 sub-packet this is from the source
     * packet ID and map it to an {@code action} VarInt.
     */
    private ByteBuf convertSplitToUnified(ByteBuf in, PacketTranslationContext ctx) {
        // Determine action code from the server-side packet ID
        int sourceId   = ctx.serverAdapter().packetIdTitle();
        int actionCode = resolveActionCode(sourceId, ctx);

        ByteBuf out = in.alloc().buffer(in.readableBytes() + 4);
        writeVarInt(out, actionCode);
        switch (actionCode) {
            case ACTION_TITLE, ACTION_SUBTITLE, ACTION_ACTIONBAR -> {
                String json = readString(in);
                writeString(out, json);
            }
            case ACTION_TIMES -> {
                out.writeInt(in.readInt());
                out.writeInt(in.readInt());
                out.writeInt(in.readInt());
            }
            case ACTION_HIDE -> {} // nothing follows
            case ACTION_RESET -> {
                if (in.isReadable()) in.readBoolean(); // consume reset flag
            }
        }
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Maps a 1.17+ title-related server packet ID to its pre-1.17 action code.
     */
    private int resolveActionCode(int packetId, PacketTranslationContext ctx) {
        // 1.17 title IDs: 0x59 (title), 0x57 (subtitle), 0x41 (action bar), 0x5A (times), 0x10 (clear)
        if (packetId == 0x59) return ACTION_TITLE;
        if (packetId == 0x57) return ACTION_SUBTITLE;
        if (packetId == 0x41) return ACTION_ACTIONBAR;
        if (packetId == 0x5A) return ACTION_TIMES;
        if (packetId == 0x10) return ACTION_HIDE;
        return ACTION_TITLE; // safe fallback
    }

    private static String readString(ByteBuf buf) {
        int len = readVarInt(buf);
        if (len <= 0) return "{}";
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
        int r = 0, s = 0;
        while (buf.isReadable()) {
            byte b = buf.readByte();
            r |= (b & 0x7F) << s;
            if ((b & 0x80) == 0) return r;
            s += 7;
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

package net.lega.protocol.translation.impl;

/**
 * Translates the Spawn Entity / Spawn Mob packets between versions.
 *
 * <h3>Changes handled</h3>
 * <ul>
 *   <li><b>1.9</b>: {@code Spawn Object} (0x00) gained a UUID field; Spawn Mob (0x03)
 *       also gained UUID; {@code objectData} encoding changed.</li>
 *   <li><b>1.14</b>: {@code Spawn Object} and {@code Spawn Mob} were merged into a
 *       single {@code Spawn Entity} packet (0x00 in 1.14).</li>
 *   <li><b>1.19</b>: Entity spawn packet gained a head-yaw byte.</li>
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

public final class EntitySpawnTranslator implements PacketTranslator {

    private static final int PROTOCOL_1_9  = MinecraftVersion.V1_9.getProtocolId();
    private static final int PROTOCOL_1_14 = MinecraftVersion.V1_14.getProtocolId();
    private static final int PROTOCOL_1_19 = MinecraftVersion.V1_19.getProtocolId();

    @Override
    public int sourcePacketId(PacketTranslationContext ctx) {
        return ctx.serverAdapter().packetIdSpawnEntity();
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
        // Applies when crossing the 1.9, 1.14 or 1.19 spawn-entity boundary
        return crossesBoundary(cl, sv, PROTOCOL_1_9)
            || crossesBoundary(cl, sv, PROTOCOL_1_14)
            || crossesBoundary(cl, sv, PROTOCOL_1_19);
    }

    @Override
    public ByteBuf translate(ByteBuf in, PacketTranslationContext ctx) {
        int clientProto = ctx.clientProtocol();
        int serverProto = ctx.serverVersion().getProtocolId();

        // Server is >= 1.14 (merged spawn), client is < 1.14 (separate spawn-mob/spawn-object)
        if (serverProto >= PROTOCOL_1_14 && clientProto < PROTOCOL_1_14) {
            return downgradeSpawnEntityTo113(in, ctx, clientProto);
        }
        // Server is < 1.9 (no UUID), client is >=1.9 (needs UUID)
        if (serverProto < PROTOCOL_1_9 && clientProto >= PROTOCOL_1_9) {
            return addUuidToSpawnEntity(in, ctx);
        }
        // Server is >=1.9, client is <1.9 (strip UUID)
        if (serverProto >= PROTOCOL_1_9 && clientProto < PROTOCOL_1_9) {
            return stripUuidFromSpawnEntity(in, ctx);
        }
        // Handle 1.19 head-yaw field
        if (serverProto >= PROTOCOL_1_19 && clientProto < PROTOCOL_1_19) {
            return stripHeadYaw(in, ctx);
        }
        if (serverProto < PROTOCOL_1_19 && clientProto >= PROTOCOL_1_19) {
            return addHeadYaw(in, ctx);
        }
        // No applicable translation — return verbatim
        return in.retain();
    }

    // ── 1.14+ merged spawn → pre-1.14 split spawn ─────────────────────────────

    /**
     * 1.14 Spawn Entity wire format:
     * entityId (VarInt), uuid (UUID 2×long), type (VarInt),
     * x/y/z (double×3), pitch/yaw (byte×2), velocityX/Y/Z (short×3)
     *
     * Pre-1.14 Spawn Object (0x00 / 0x0E):
     * entityId (VarInt), uuid (UUID 2×long, since 1.9),
     * type (byte), x/y/z (double×3), pitch/yaw (byte×2),
     * objectData (int), velocityX/Y/Z (short×3 if objectData≠0)
     */
    private ByteBuf downgradeSpawnEntityTo113(ByteBuf in, PacketTranslationContext ctx, int clientProto) {
        ByteBuf out = in.alloc().buffer(64);
        // Entity ID
        int eid = readVarInt(in);
        writeVarInt(out, eid);
        // UUID (present since 1.9)
        if (clientProto >= PROTOCOL_1_9) {
            out.writeLong(in.readLong());
            out.writeLong(in.readLong());
        } else {
            in.skipBytes(16); // skip UUID in server format
        }
        // Type: VarInt in 1.14, byte in older
        int entityType = readVarInt(in);
        if (clientProto >= PROTOCOL_1_13) {
            writeVarInt(out, entityType);
        } else {
            out.writeByte(entityType & 0xFF);
        }
        // Position (double×3)
        out.writeDouble(in.readDouble()); // x
        out.writeDouble(in.readDouble()); // y
        out.writeDouble(in.readDouble()); // z
        // Pitch, Yaw
        out.writeByte(in.readByte()); // pitch
        out.writeByte(in.readByte()); // yaw
        // Head yaw (1.19+) — skip if present in source
        // objectData / data field
        out.writeInt(0); // objectData=0 means no velocity follows in old format
        // Velocity (skip from source; old client won't get it since objectData=0)
        if (in.readableBytes() >= 6) {
            in.skipBytes(6);
        }
        return out;
    }

    // ── Add UUID for 1.9+ clients receiving a 1.8 spawn ──────────────────────

    private ByteBuf addUuidToSpawnEntity(ByteBuf in, PacketTranslationContext ctx) {
        int eid = readVarInt(in);
        ByteBuf out = in.alloc().buffer(in.readableBytes() + 18);
        writeVarInt(out, eid);
        // Inject a deterministic UUID based on entity ID
        UUID uuid = new UUID(0xDEADBEEF_00000000L | eid, eid);
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeBytes(in);
        return out;
    }

    // ── Strip UUID for clients < 1.9 receiving a 1.9+ spawn ─────────────────

    private ByteBuf stripUuidFromSpawnEntity(ByteBuf in, PacketTranslationContext ctx) {
        int eid = readVarInt(in);
        in.skipBytes(16); // skip UUID
        ByteBuf out = in.alloc().buffer(in.readableBytes() + 5);
        writeVarInt(out, eid);
        out.writeBytes(in);
        return out;
    }

    // ── Add / strip head-yaw (1.19 boundary) ─────────────────────────────────

    private ByteBuf stripHeadYaw(ByteBuf in, PacketTranslationContext ctx) {
        // Skip to where head-yaw would be: after eid(VarInt)+uuid(16)+type(VarInt)+24+2
        ByteBuf out = in.alloc().buffer(in.readableBytes());
        // Copy everything except the head-yaw byte that comes after pitch+yaw
        // Read: entityId, uuid, type, x, y, z, pitch, yaw — then skip headYaw
        copyVarInt(in, out);       // entity ID
        out.writeLong(in.readLong()); out.writeLong(in.readLong()); // UUID
        copyVarInt(in, out);       // type
        out.writeDouble(in.readDouble()); // x
        out.writeDouble(in.readDouble()); // y
        out.writeDouble(in.readDouble()); // z
        out.writeByte(in.readByte()); // pitch
        out.writeByte(in.readByte()); // yaw
        in.skipBytes(1); // skip head-yaw
        out.writeBytes(in);
        return out;
    }

    private ByteBuf addHeadYaw(ByteBuf in, PacketTranslationContext ctx) {
        ByteBuf out = in.alloc().buffer(in.readableBytes() + 1);
        copyVarInt(in, out);
        out.writeLong(in.readLong()); out.writeLong(in.readLong());
        copyVarInt(in, out);
        out.writeDouble(in.readDouble());
        out.writeDouble(in.readDouble());
        out.writeDouble(in.readDouble());
        out.writeByte(in.readByte()); // pitch
        byte yaw = in.readByte();
        out.writeByte(yaw);      // yaw
        out.writeByte(yaw);      // head-yaw (duplicate yaw as approximation)
        out.writeBytes(in);
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static boolean crossesBoundary(int a, int b, int boundary) {
        return (a >= boundary) != (b >= boundary);
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

    private static void copyVarInt(ByteBuf in, ByteBuf out) {
        while (in.isReadable()) {
            byte b = in.readByte();
            out.writeByte(b);
            if ((b & 0x80) == 0) return;
        }
    }
}

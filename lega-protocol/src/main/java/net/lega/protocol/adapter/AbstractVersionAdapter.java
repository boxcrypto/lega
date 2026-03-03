package net.lega.protocol.adapter;

import io.netty.buffer.ByteBuf;
import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.VersionAdapter;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * Abstract base class providing default implementations for all
 * {@link VersionAdapter} methods.
 *
 * <p>Concrete adapters only override methods where their version
 * differs from the baseline (1.7.x) behaviour.</p>
 *
 * <h2>VarInt encoding (standard, all versions ≥ 1.7)</h2>
 * <pre>
 *   while (value & ~0x7F) != 0:
 *       write((value & 0x7F) | 0x80)
 *       value >>>= 7
 *   write(value & 0x7F)
 * </pre>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public abstract class AbstractVersionAdapter implements VersionAdapter {

    /** Versions handled by this adapter (populated by subclass constructor). */
    protected final Set<MinecraftVersion> supported;

    protected AbstractVersionAdapter(MinecraftVersion... versions) {
        supported = EnumSet.noneOf(MinecraftVersion.class);
        for (MinecraftVersion v : versions) {
            supported.add(v);
        }
    }

    // ── identity ──────────────────────────────────────────────────────────

    @Override
    public Set<MinecraftVersion> supportedVersions() {
        return java.util.Collections.unmodifiableSet(supported);
    }

    // ── feature defaults (1.7.x baseline) ────────────────────────────────

    @Override public boolean supportsOffHand()          { return false; }
    @Override public boolean supportsFlattening()       { return false; }
    @Override public boolean supportsChatComponents()   { return false; }
    @Override public boolean supportsChatSigning()      { return false; }
    @Override public boolean supportsItemComponents()   { return false; }
    @Override public boolean supportsNewChunkFormat()   { return false; }
    @Override public boolean supportsUUID()             { return false; }
    @Override public boolean supportsActionBar()        { return false; }
    @Override public boolean supportsBossBar()          { return false; }
    @Override public boolean supportsTitles()           { return false; }
    @Override public boolean supportsHexColour()        { return false; }
    @Override public boolean supportsBundle()           { return false; }
    @Override public boolean supportsSecureChatHello()  { return false; }
    @Override public boolean supportsServerIcon()       { return false; }

    // ── default packet IDs — return −1 where unsupported ─────────────────

    @Override public int packetIdActionBar()          { return -1; }
    @Override public int packetIdBossBar()            { return -1; }
    @Override public int packetIdTitle()              { return -1; }
    @Override public int packetIdTabListHeaderFooter(){ return -1; }

    // ── VarInt (shared by all versions) ───────────────────────────────────

    @Override
    public int readVarInt(ByteBuf buf) {
        int value = 0, shift = 0, b;
        do {
            b = buf.readByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new RuntimeException("VarInt too large");
        } while ((b & 0x80) != 0);
        return value;
    }

    @Override
    public void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value & 0x7F);
    }

    // ── position helpers ──────────────────────────────────────────────────

    @Override
    public double[] readPosition(ByteBuf buf) {
        double x     = buf.readDouble();
        double y     = buf.readDouble();
        double z     = buf.readDouble();
        float  yaw   = buf.readFloat();
        float  pitch = buf.readFloat();
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void writePosition(ByteBuf buf, double x, double y, double z, float yaw, float pitch) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    // ── chat / title helpers ──────────────────────────────────────────────

    /**
     * Wraps a plain string in a minimal JSON text component.
     * Used by old versions that always expect JSON.
     */
    protected String toJson(String text) {
        // Escape only what is strictly necessary
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"text\":\"" + escaped + "\"}";
    }

    /**
     * Writes a UTF-8 string preceded by its VarInt byte-length.
     */
    protected void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    /**
     * Reads a UTF-8 string (VarInt length prefix).
     */
    protected String readString(ByteBuf buf) {
        int len = readVarInt(buf);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── block state translation (default: identity, for pre-1.13) ─────────

    @Override
    public int translateBlockState(int raw) {
        // Pre-flattening: block=(raw >> 4), meta=(raw & 0xF)
        // For modern use each adapter should override with a full mapping table.
        return raw;
    }

    // ── disconnect encoding ───────────────────────────────────────────────

    @Override
    public byte[] encodeDisconnect(String reason) {
        ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        try {
            writeVarInt(buf, packetIdDisconnect());
            writeString(buf, toJson(reason));
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    // ── default title encoding (no-op for versions without titles) ─────────

    @Override
    public byte[] encodeTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // Versions that don't support titles return empty array
        return new byte[0];
    }
}

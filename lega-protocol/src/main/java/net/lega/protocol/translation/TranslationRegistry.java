package net.lega.protocol.translation;

/**
 * Singleton registry of all built-in {@link PacketTranslator} instances.
 *
 * <p>Translators are auto-registered at class-load time via
 * {@link #registerBuiltins()}.  External modules may call
 * {@link #register(PacketTranslator)} to add custom translators.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.protocol.translation.impl.ChatPacketTranslator;
import net.lega.protocol.translation.impl.ChunkDataTranslator;
import net.lega.protocol.translation.impl.EntitySpawnTranslator;
import net.lega.protocol.translation.impl.TitlePacketTranslator;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TranslationRegistry {

    private static final TranslationRegistry INSTANCE = new TranslationRegistry();

    /** All registered translators, consulted in insertion order. */
    private final List<PacketTranslator> translators = new CopyOnWriteArrayList<>();

    private TranslationRegistry() {
        registerBuiltins();
    }

    public static TranslationRegistry getInstance() {
        return INSTANCE;
    }

    // ── mutation ───────────────────────────────────────────────────────────────

    /** Registers a custom translator.  Translators added later take priority. */
    public void register(PacketTranslator translator) {
        Objects.requireNonNull(translator, "translator must not be null");
        translators.add(translator);
    }

    // ── lookup ─────────────────────────────────────────────────────────────────

    /**
     * Returns all translators that apply to the given context and match the
     * given source packet ID, in registration order.
     */
    public List<PacketTranslator> find(int sourcePacketId,
                                       PacketTranslationContext ctx) {
        List<PacketTranslator> result = new ArrayList<>(4);
        for (PacketTranslator t : translators) {
            if (t.direction() == ctx.direction()
                    && t.sourcePacketId(ctx) == sourcePacketId
                    && t.appliesTo(ctx)) {
                result.add(t);
            }
        }
        return result;
    }

    // ── built-ins ─────────────────────────────────────────────────────────────

    private void registerBuiltins() {
        translators.add(new ChatPacketTranslator());
        translators.add(new ChunkDataTranslator());
        translators.add(new EntitySpawnTranslator());
        translators.add(new TitlePacketTranslator());
    }
}

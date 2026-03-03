package net.lega.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that maps every {@link MinecraftVersion} to a
 * {@link VersionAdapter} instance.
 *
 * <p>Adapters are registered eagerly at class-load time via
 * {@link #register(VersionAdapter)}. The registry picks the best
 * available adapter for a version using exact match first, then
 * falls back to the nearest older adapter.</p>
 *
 * <pre>{@code
 * VersionAdapter a = VersionAdapterRegistry.get().bestFor(MinecraftVersion.V1_21_4);
 * }</pre>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public final class VersionAdapterRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionAdapterRegistry.class);

    /** Singleton instance. */
    private static final VersionAdapterRegistry INSTANCE = new VersionAdapterRegistry();

    /** version → adapter (exact and fallback). */
    private final Map<MinecraftVersion, VersionAdapter> adapters = new ConcurrentHashMap<>();

    /** Ordered list of registered primary versions for fallback lookup. */
    private final List<MinecraftVersion> orderedPrimaries = new ArrayList<>();

    private VersionAdapterRegistry() {
        loadBuiltinAdapters();
    }

    public static VersionAdapterRegistry get() { return INSTANCE; }

    // ── registration ──────────────────────────────────────────────────────

    /**
     * Registers an adapter. All versions in
     * {@link VersionAdapter#supportedVersions()} are mapped to it.
     *
     * @param adapter the adapter to register (not null)
     */
    public synchronized void register(VersionAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter cannot be null");
        for (MinecraftVersion v : adapter.supportedVersions()) {
            VersionAdapter previous = adapters.put(v, adapter);
            if (previous != null) {
                LOGGER.debug("Replaced adapter for {} ({} → {})",
                        v, previous.getClass().getSimpleName(),
                        adapter.getClass().getSimpleName());
            }
        }
        MinecraftVersion primary = adapter.primaryVersion();
        if (!orderedPrimaries.contains(primary)) {
            orderedPrimaries.add(primary);
            orderedPrimaries.sort(Comparator.comparingInt(Enum::ordinal));
        }
        LOGGER.debug("Registered {} covering {} versions", adapter.describe(),
                adapter.supportedVersions().size());
    }

    // ── lookup ────────────────────────────────────────────────────────────

    /**
     * Returns the exact adapter registered for {@code version}, or
     * {@code null} if none is registered.
     */
    public VersionAdapter exact(MinecraftVersion version) {
        return adapters.get(version);
    }

    /**
     * Returns the best adapter for {@code version}:
     * <ol>
     *   <li>Exact match.</li>
     *   <li>Most recent adapter whose primary version ≤ this version.</li>
     *   <li>The oldest registered adapter (1.7.x fallback).</li>
     * </ol>
     *
     * @throws IllegalStateException if no adapters have been registered at all
     */
    public VersionAdapter bestFor(MinecraftVersion version) {
        VersionAdapter exact = adapters.get(version);
        if (exact != null) return exact;

        // Walk backwards through declared versions to find closest older adapter
        for (int i = orderedPrimaries.size() - 1; i >= 0; i--) {
            MinecraftVersion candidate = orderedPrimaries.get(i);
            if (candidate.isOlderThan(version) || candidate == version) {
                VersionAdapter adapter = adapters.get(candidate);
                if (adapter != null) {
                    LOGGER.debug("Using {} as fallback for {}", adapter.describe(), version);
                    return adapter;
                }
            }
        }

        // Last resort: the oldest registered adapter
        if (!orderedPrimaries.isEmpty()) {
            return adapters.get(orderedPrimaries.get(0));
        }

        throw new IllegalStateException("No adapters registered in VersionAdapterRegistry");
    }

    /**
     * Resolves a protocol ID from a handshake packet to the best adapter.
     *
     * @param  rawProtocolId the VarInt from the handshake packet
     * @return the best-matching adapter, or {@code null} if completely unknown
     */
    public VersionAdapter fromProtocol(int rawProtocolId) {
        MinecraftVersion version = MinecraftVersion.fromProtocolId(rawProtocolId);
        if (version != null) return bestFor(version);
        LOGGER.warn("Unknown protocol ID {} — will use legacy 1.7 adapter as fallback", rawProtocolId);
        return adapters.get(MinecraftVersion.V1_7_10);
    }

    /**
     * Returns an unmodifiable view of all registered adapters.
     */
    public Map<MinecraftVersion, VersionAdapter> all() {
        return Collections.unmodifiableMap(adapters);
    }

    /**
     * Number of distinct versions with registered adapters.
     */
    public int size() { return adapters.size(); }

    // ── built-in adapter loading ──────────────────────────────────────────

    private void loadBuiltinAdapters() {
        // Load each built-in adapter (ordered oldest → newest)
        List<String> adapterClasses = List.of(
            "net.lega.protocol.adapter.v1_7.Adapter_1_7",
            "net.lega.protocol.adapter.v1_8.Adapter_1_8",
            "net.lega.protocol.adapter.v1_9.Adapter_1_9",
            "net.lega.protocol.adapter.v1_10.Adapter_1_10",
            "net.lega.protocol.adapter.v1_11.Adapter_1_11",
            "net.lega.protocol.adapter.v1_12.Adapter_1_12",
            "net.lega.protocol.adapter.v1_13.Adapter_1_13",
            "net.lega.protocol.adapter.v1_14.Adapter_1_14",
            "net.lega.protocol.adapter.v1_15.Adapter_1_15",
            "net.lega.protocol.adapter.v1_16.Adapter_1_16",
            "net.lega.protocol.adapter.v1_17.Adapter_1_17",
            "net.lega.protocol.adapter.v1_18.Adapter_1_18",
            "net.lega.protocol.adapter.v1_19.Adapter_1_19",
            "net.lega.protocol.adapter.v1_20.Adapter_1_20",
            "net.lega.protocol.adapter.v1_21.Adapter_1_21"
        );

        for (String className : adapterClasses) {
            try {
                Class<?> cls = Class.forName(className);
                VersionAdapter adapter = (VersionAdapter) cls.getDeclaredConstructor().newInstance();
                register(adapter);
            } catch (Exception e) {
                LOGGER.error("Failed to load built-in adapter {}: {}", className, e.getMessage());
            }
        }

        LOGGER.info("VersionAdapterRegistry: {} versions covered by {} adapter classes",
                adapters.size(), orderedPrimaries.size());
    }
}

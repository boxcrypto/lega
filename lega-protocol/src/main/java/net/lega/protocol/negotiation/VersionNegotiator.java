package net.lega.protocol.negotiation;

/**
 * Evaluates an incoming handshake protocol ID against the server's enabled
 * version list and exposes the outcome to the rest of the Netty pipeline.
 *
 * <p>Thread-safe; the {@link VersionConfig}-backed set is set once at startup.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.VersionAdapter;
import net.lega.protocol.VersionAdapterRegistry;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

public final class VersionNegotiator {

    private final MinecraftVersion defaultVersion;
    private final Set<MinecraftVersion> enabledVersions;
    private final VersionAdapterRegistry registry;

    public VersionNegotiator(
            MinecraftVersion defaultVersion,
            Set<MinecraftVersion> enabledVersions) {
        this.defaultVersion = defaultVersion;
        // Defensive copy into an enum set for O(1) contains
        this.enabledVersions = Collections.unmodifiableSet(
                EnumSet.copyOf(enabledVersions));
        this.registry = VersionAdapterRegistry.get();
    }

    // ── public API ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the supplied raw protocol ID corresponds to a
     * MC version in the enabled set, or – if the protocol ID is unrecognised –
     * when its nearest older adapter exists.
     */
    public boolean isAllowed(int rawProtocolId) {
        MinecraftVersion exact = MinecraftVersion.fromProtocolId(rawProtocolId);
        if (exact != null) {
            return enabledVersions.contains(exact);
        }
        // For snapshot or future protocols, accept if we have an adapter and
        // the protocol is within the range of any enabled version's family
        MinecraftVersion fallback = nearestKnown(rawProtocolId);
        return fallback != null && enabledVersions.stream()
                .anyMatch(v -> v.getProtocolId() <= rawProtocolId);
    }

    /**
     * Resolves the best {@link MinecraftVersion} enum constant for the given
     * raw protocol ID.  Returns {@link #defaultVersion} when nothing matches.
     */
    public MinecraftVersion resolve(int rawProtocolId) {
        MinecraftVersion exact = MinecraftVersion.fromProtocolId(rawProtocolId);
        if (exact != null) return exact;
        MinecraftVersion best = nearestKnown(rawProtocolId);
        return best != null ? best : defaultVersion;
    }

    /**
     * Returns the {@link VersionAdapter} to use for a connection after the
     * version has been resolved.  The registry's fallback logic ensures we
     * always return a useful adapter even for unknown protocols.
     */
    public VersionAdapter adapterFor(MinecraftVersion clientVersion) {
        VersionAdapter exact = registry.exact(clientVersion);
        if (exact != null) return exact;
        return registry.bestFor(clientVersion);
    }

    public MinecraftVersion getDefaultVersion() {
        return defaultVersion;
    }

    public Set<MinecraftVersion> getEnabledVersions() {
        return enabledVersions;
    }

    /**
     * Builds a human-readable list of enabled versions for the status/ping MOTD.
     */
    public String enabledVersionRange() {
        if (enabledVersions.isEmpty()) return defaultVersion.getDisplayName();
        MinecraftVersion min = enabledVersions.stream()
                .min(java.util.Comparator.comparingInt(MinecraftVersion::getProtocolId))
                .orElse(defaultVersion);
        MinecraftVersion max = enabledVersions.stream()
                .max(java.util.Comparator.comparingInt(MinecraftVersion::getProtocolId))
                .orElse(defaultVersion);
        if (min == max) return min.getDisplayName();
        return min.getDisplayName() + " – " + max.getDisplayName();
    }

    // ── private ────────────────────────────────────────────────────────────────

    /** Finds the known version with the highest protocol ID ≤ {@code rawProtocolId}. */
    private MinecraftVersion nearestKnown(int rawProtocolId) {
        MinecraftVersion best = null;
        for (MinecraftVersion v : MinecraftVersion.values()) {
            if (v.getProtocolId() <= rawProtocolId) {
                if (best == null || v.getProtocolId() > best.getProtocolId()) {
                    best = v;
                }
            }
        }
        return best;
    }
}

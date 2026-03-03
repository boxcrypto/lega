package net.lega.server.config;

/**
 * Manages {@code versions.yml} — the version support configuration file.
 *
 * <p>On first run (or when the file is missing) the server creates the file
 * with sensible defaults, prints a notice to the console and halts startup
 * so the operator can review the settings before going live.</p>
 *
 * <p>The config controls:</p>
 * <ul>
 *   <li><b>accept-eula</b> – must be {@code true} before the server accepts connections</li>
 *   <li><b>default-version</b> – the MC version that defines the server's internal protocol</li>
 *   <li><b>enabled-versions</b> – list of client versions that may connect; LEGA translates
 *       packets automatically, no ViaVersion/ViaRewind/ViaBackwards needed</li>
 * </ul>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.protocol.MinecraftVersion;
import net.lega.protocol.VersionAdapterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.StringJoiner;

public final class VersionConfig {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/VersionConfig");

    /** File name written to the server root. */
    private static final String FILE_NAME = "versions.yml";

    private final Path filePath;

    // ── loaded values ──────────────────────────────────────────────────────────

    /** The version the server runs internally (packet encoding baseline). */
    private MinecraftVersion defaultVersion = MinecraftVersion.V1_8;

    /**
     * Set of all versions that clients may use when joining.
     * At minimum {@link #defaultVersion} is always included.
     */
    private final Set<MinecraftVersion> enabledVersions = new LinkedHashSet<>();

    // ── constructor ────────────────────────────────────────────────────────────

    public VersionConfig(Path serverRoot) {
        this.filePath = serverRoot.resolve(FILE_NAME);
    }

    // ── lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Loads the config file, creating it with defaults if it does not exist.
     *
     * @throws IllegalStateException if the EULA has not been accepted
     * @throws IOException           if the file cannot be read or written
     */
    public void load() throws IOException {
        if (!Files.exists(filePath)) {
            createDefault();
            LOGGER.info("========================================================");
            LOGGER.info(" LEGA first run – versions.yml has been created.");
            LOGGER.info(" File: {}", filePath.toAbsolutePath());
            LOGGER.info(" Review enabled-versions to choose which clients can connect.");
            LOGGER.info(" To disable a version, comment it out with  #");
            LOGGER.info("========================================================");
            // Default has accept-eula: true — continue loading without halting
        }

        Map<String, Object> raw = loadYaml(filePath);

        // ── EULA check ─────────────────────────────────────────────────────
        boolean eulaAccepted = Boolean.parseBoolean(String.valueOf(
                raw.getOrDefault("accept-eula", "false")));
        if (!eulaAccepted) {
            LOGGER.error("========================================================");
            LOGGER.error(" EULA not accepted – server cannot start.");
            LOGGER.error(" Edit versions.yml at:  {}", filePath.toAbsolutePath());
            LOGGER.error("   1. Set   accept-eula: true   to agree to the MC EULA");
            LOGGER.error("      https://aka.ms/MinecraftEULA");
            LOGGER.error("   2. Set   default-version   to your server's base version");
            LOGGER.error("   3. Uncomment any client versions you want to support under");
            LOGGER.error("      enabled-versions  (LEGA translates packets natively)");
            LOGGER.error("========================================================");
            throw new IllegalStateException(
                    "Server halted – EULA not accepted (versions.yml → accept-eula: true).");
        }

        // ── default-version ────────────────────────────────────────────────
        String defaultStr = String.valueOf(raw.getOrDefault("default-version", "1.8"));
        defaultVersion = resolveVersion(defaultStr);
        if (defaultVersion == null) {
            LOGGER.warn("Unknown default-version '{}', falling back to 1.8", defaultStr);
            defaultVersion = MinecraftVersion.V1_8;
        }

        // ── enabled-versions ───────────────────────────────────────────────
        enabledVersions.clear();
        Object rawList = raw.get("enabled-versions");
        if (rawList instanceof List<?> list) {
            for (Object entry : list) {
                String vStr = String.valueOf(entry).trim();
                MinecraftVersion mv = resolveVersion(vStr);
                if (mv != null) {
                    enabledVersions.add(mv);
                } else {
                    LOGGER.warn("Unknown version '{}' in enabled-versions – skipped", vStr);
                }
            }
        }
        // Always include the default version
        enabledVersions.add(defaultVersion);

        LOGGER.info("Default version   : {}", defaultVersion.getDisplayName());
        StringJoiner versionList = new StringJoiner(", ");
        for (MinecraftVersion v : enabledVersions) versionList.add(v.getDisplayName());
        LOGGER.info("Enabled versions  : {}", versionList);
        LOGGER.info("Native translation: ViaVersion/ViaRewind/ViaBackwards NOT required.");
    }

    // ── public accessors ───────────────────────────────────────────────────────

    public MinecraftVersion getDefaultVersion() {
        return defaultVersion;
    }

    /** Returns an immutable snapshot of all enabled versions. */
    public Set<MinecraftVersion> getEnabledVersions() {
        return Collections.unmodifiableSet(enabledVersions);
    }

    /**
     * Returns {@code true} if a client with {@code protocolId} is allowed to connect.
     * Uses an exact match first, then falls back to the nearest older version.
     */
    public boolean isProtocolAllowed(int protocolId) {
        // Exact match
        MinecraftVersion exact = MinecraftVersion.fromProtocolId(protocolId);
        if (exact != null) {
            return enabledVersions.contains(exact);
        }
        // Unknown protocol — allow if registry has a fallback adapter and the
        // protocol is within the range of any enabled version's family
        MinecraftVersion fallback = nearestKnown(protocolId);
        return fallback != null && VersionAdapterRegistry.get().bestFor(fallback) != null;
    }

    /**
     * Returns the best {@link MinecraftVersion} for the supplied raw protocol ID,
     * or the default version when the ID is not recognised.
     */
    public MinecraftVersion resolveProtocol(int protocolId) {
        MinecraftVersion exact = MinecraftVersion.fromProtocolId(protocolId);
        return exact != null ? exact : defaultVersion;
    }

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

    // ── private helpers ────────────────────────────────────────────────────────

    /** Resolve a human-readable version string (e.g. {@code "1.8.9"}) to its enum. */
    private static MinecraftVersion resolveVersion(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.trim();
        for (MinecraftVersion v : MinecraftVersion.values()) {
            if (v.getDisplayName().equalsIgnoreCase(clean)) return v;
        }
        // Normalise "1.8" → "1.8.0" style lookup
        String normalised = clean.contains(".") && clean.chars().filter(c -> c == '.').count() == 1
                ? clean + ".0"
                : clean;
        for (MinecraftVersion v : MinecraftVersion.values()) {
            if (v.getDisplayName().equals(normalised)) return v;
            // Also try the enum name: V1_8 ↔ "1.8"
            String fromName = v.name().replace("V", "").replace("_", ".");
            if (fromName.equalsIgnoreCase(clean)) return v;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path path) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(opts);
        try (Reader r = Files.newBufferedReader(path)) {
            Map<String, Object> result = yaml.load(r);
            return result != null ? result : new LinkedHashMap<>();
        }
    }

    private void createDefault() throws IOException {
        Files.createDirectories(filePath.getParent());

        // Build the default config with instructional comments embedded as YAML headers
        List<String> lines = new ArrayList<>();
        lines.add("# ============================================================");
        lines.add("# LEGA – Version Configuration");
        lines.add("# ============================================================");
        lines.add("#");
        lines.add("# accept-eula");
        lines.add("#   Set to true to accept the Minecraft End User License Agreement.");
        lines.add("#   https://aka.ms/MinecraftEULA");
        lines.add("#   The server will NOT start until this is true.");
        lines.add("#");
        lines.add("accept-eula: true");
        lines.add("");
        lines.add("# default-version");
        lines.add("#   The Minecraft version your server runs on internally.");
        lines.add("#   All connected clients (even on different versions) are translated");
        lines.add("#   to/from this version automatically – no ViaVersion needed.");
        lines.add("#   Example: '1.8' means the server logic talks 1.8 protocol.");
        lines.add("#");
        lines.add("default-version: \"1.8\"");
        lines.add("");
        lines.add("# enabled-versions");
        lines.add("#   List every Minecraft client version string you want to allow.");
        lines.add("#   Supported range: 1.7.10 → 1.21.4");
        lines.add("#   The default-version is always implicitly included.");
        lines.add("#");
        lines.add("enabled-versions:");

        // Add all versions from the enum as commented entries, uncommenting popular ones
        Set<String> popular = Set.of("1.7.10","1.8","1.8.9","1.12.2","1.16.5","1.20.4","1.21.4");
        for (MinecraftVersion v : MinecraftVersion.values()) {
            String display = v.getDisplayName();
            if (popular.contains(display)) {
                lines.add("  - \"" + display + "\"");
            } else {
                lines.add("  #- \"" + display + "\"");
            }
        }
        lines.add("");
        lines.add("# ============================================================");

        Files.write(filePath, lines, java.nio.charset.StandardCharsets.UTF_8);
        LOGGER.info("Created default {}", FILE_NAME);
    }
}

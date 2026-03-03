package net.lega.protocol;

/**
 * Enumeration of every Minecraft version supported by LEGA,
 * from 1.7.2 to 1.21.4, with their respective protocol IDs.
 *
 * <p>Protocol IDs are used during the handshake phase to detect
 * which {@link VersionAdapter} should handle the connection.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public enum MinecraftVersion {

    // ── 1.7.x ──────────────────────────────────────────────────────────────
    V1_7_2 (4,   1, 7, 2,  VersionFamily.LEGACY),
    V1_7_10(5,   1, 7, 10, VersionFamily.LEGACY),

    // ── 1.8.x ──────────────────────────────────────────────────────────────
    V1_8   (47,  1, 8, 0,  VersionFamily.PRE_NETTY),
    V1_8_9 (47,  1, 8, 9,  VersionFamily.PRE_NETTY),

    // ── 1.9.x ──────────────────────────────────────────────────────────────
    V1_9   (107, 1, 9, 0,  VersionFamily.COMBAT_UPDATE),
    V1_9_2 (109, 1, 9, 2,  VersionFamily.COMBAT_UPDATE),
    V1_9_4 (110, 1, 9, 4,  VersionFamily.COMBAT_UPDATE),

    // ── 1.10.x ─────────────────────────────────────────────────────────────
    V1_10  (210, 1, 10, 0, VersionFamily.FROSTBURN),

    // ── 1.11.x ─────────────────────────────────────────────────────────────
    V1_11  (315, 1, 11, 0, VersionFamily.EXPLORATION),
    V1_11_2(316, 1, 11, 2, VersionFamily.EXPLORATION),

    // ── 1.12.x ─────────────────────────────────────────────────────────────
    V1_12  (335, 1, 12, 0, VersionFamily.WORLD_OF_COLOR),
    V1_12_1(338, 1, 12, 1, VersionFamily.WORLD_OF_COLOR),
    V1_12_2(340, 1, 12, 2, VersionFamily.WORLD_OF_COLOR),

    // ── 1.13.x ─────────────────────────────────────────────────────────────
    V1_13  (393, 1, 13, 0, VersionFamily.UPDATE_AQUATIC),
    V1_13_1(401, 1, 13, 1, VersionFamily.UPDATE_AQUATIC),
    V1_13_2(404, 1, 13, 2, VersionFamily.UPDATE_AQUATIC),

    // ── 1.14.x ─────────────────────────────────────────────────────────────
    V1_14  (477, 1, 14, 0, VersionFamily.VILLAGE_AND_PILLAGE),
    V1_14_1(480, 1, 14, 1, VersionFamily.VILLAGE_AND_PILLAGE),
    V1_14_2(485, 1, 14, 2, VersionFamily.VILLAGE_AND_PILLAGE),
    V1_14_3(490, 1, 14, 3, VersionFamily.VILLAGE_AND_PILLAGE),
    V1_14_4(498, 1, 14, 4, VersionFamily.VILLAGE_AND_PILLAGE),

    // ── 1.15.x ─────────────────────────────────────────────────────────────
    V1_15  (573, 1, 15, 0, VersionFamily.BUZZY_BEES),
    V1_15_1(575, 1, 15, 1, VersionFamily.BUZZY_BEES),
    V1_15_2(578, 1, 15, 2, VersionFamily.BUZZY_BEES),

    // ── 1.16.x ─────────────────────────────────────────────────────────────
    V1_16  (735, 1, 16, 0, VersionFamily.NETHER_UPDATE),
    V1_16_1(736, 1, 16, 1, VersionFamily.NETHER_UPDATE),
    V1_16_2(751, 1, 16, 2, VersionFamily.NETHER_UPDATE),
    V1_16_3(753, 1, 16, 3, VersionFamily.NETHER_UPDATE),
    V1_16_4(754, 1, 16, 4, VersionFamily.NETHER_UPDATE),
    V1_16_5(754, 1, 16, 5, VersionFamily.NETHER_UPDATE),

    // ── 1.17.x ─────────────────────────────────────────────────────────────
    V1_17  (755, 1, 17, 0, VersionFamily.CAVES_CLIFFS_1),
    V1_17_1(756, 1, 17, 1, VersionFamily.CAVES_CLIFFS_1),

    // ── 1.18.x ─────────────────────────────────────────────────────────────
    V1_18  (757, 1, 18, 0, VersionFamily.CAVES_CLIFFS_2),
    V1_18_1(757, 1, 18, 1, VersionFamily.CAVES_CLIFFS_2),
    V1_18_2(758, 1, 18, 2, VersionFamily.CAVES_CLIFFS_2),

    // ── 1.19.x ─────────────────────────────────────────────────────────────
    V1_19  (759, 1, 19, 0, VersionFamily.WILD_UPDATE),
    V1_19_1(760, 1, 19, 1, VersionFamily.WILD_UPDATE),
    V1_19_2(760, 1, 19, 2, VersionFamily.WILD_UPDATE),
    V1_19_3(761, 1, 19, 3, VersionFamily.WILD_UPDATE),
    V1_19_4(762, 1, 19, 4, VersionFamily.WILD_UPDATE),

    // ── 1.20.x ─────────────────────────────────────────────────────────────
    V1_20  (763, 1, 20, 0, VersionFamily.TRAILS_TALES),
    V1_20_1(763, 1, 20, 1, VersionFamily.TRAILS_TALES),
    V1_20_2(764, 1, 20, 2, VersionFamily.TRAILS_TALES),
    V1_20_3(765, 1, 20, 3, VersionFamily.TRAILS_TALES),
    V1_20_4(765, 1, 20, 4, VersionFamily.TRAILS_TALES),
    V1_20_5(766, 1, 20, 5, VersionFamily.TRAILS_TALES),
    V1_20_6(766, 1, 20, 6, VersionFamily.TRAILS_TALES),

    // ── 1.21.x ─────────────────────────────────────────────────────────────
    V1_21  (767, 1, 21, 0, VersionFamily.TRICKY_TRIALS),
    V1_21_1(767, 1, 21, 1, VersionFamily.TRICKY_TRIALS),
    V1_21_2(768, 1, 21, 2, VersionFamily.TRICKY_TRIALS),
    V1_21_3(768, 1, 21, 3, VersionFamily.TRICKY_TRIALS),
    V1_21_4(769, 1, 21, 4, VersionFamily.TRICKY_TRIALS);

    // ───────────────────────────────────────────────────────────────────────

    /** Minecraft network protocol ID sent during handshake. */
    private final int protocolId;

    /** Semantic version parts. */
    private final int major, minor, patch;

    /** Grouping family used for adapter sharing. */
    private final VersionFamily family;

    /** Human-readable string e.g. "1.20.4". */
    private final String displayName;

    MinecraftVersion(int protocolId, int major, int minor, int patch, VersionFamily family) {
        this.protocolId  = protocolId;
        this.major       = major;
        this.minor       = minor;
        this.patch       = patch;
        this.family      = family;
        this.displayName = major + "." + minor + (patch == 0 ? "" : "." + patch);
    }

    // ── accessors ──────────────────────────────────────────────────────────

    public int     getProtocolId()  { return protocolId; }
    public int     getMajor()       { return major; }
    public int     getMinor()       { return minor; }
    public int     getPatch()       { return patch; }
    public VersionFamily getFamily(){ return family; }
    public String  getDisplayName() { return displayName; }

    // ── utilities ──────────────────────────────────────────────────────────

    /**
     * Returns whether this version is at least as new as {@code other}.
     */
    public boolean isAtLeast(MinecraftVersion other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Returns whether this version is older than {@code other}.
     */
    public boolean isOlderThan(MinecraftVersion other) {
        return this.ordinal() < other.ordinal();
    }

    /**
     * Returns whether this version is within [min, max] (inclusive).
     */
    public boolean isBetween(MinecraftVersion min, MinecraftVersion max) {
        return this.ordinal() >= min.ordinal() && this.ordinal() <= max.ordinal();
    }

    /**
     * Returns whether this version uses the modern (1.13+) flattening data format.
     */
    public boolean isFlattened() {
        return isAtLeast(V1_13);
    }

    /**
     * Returns whether this version uses the new chunk format (1.18+ up-to-384 world height).
     */
    public boolean isNewChunkFormat() {
        return isAtLeast(V1_18);
    }

    /**
     * Returns whether this version supports the chat signing system (1.19+).
     */
    public boolean hasChatSigning() {
        return isAtLeast(V1_19);
    }

    /**
     * Returns whether this version uses NBT-based item components (1.20.5+).
     */
    public boolean hasComponentItems() {
        return isAtLeast(V1_20_5);
    }

    /**
     * Looks up a {@link MinecraftVersion} by exact protocol ID.
     * If multiple versions share the same protocol ID,
     * the latest one in declaration order is returned.
     *
     * @param protocolId raw protocol int from handshake
     * @return the matching version, or {@code null} if unknown
     */
    public static MinecraftVersion fromProtocolId(int protocolId) {
        MinecraftVersion best = null;
        for (MinecraftVersion v : values()) {
            if (v.protocolId == protocolId) {
                best = v; // last match wins (highest patch)
            }
        }
        return best;
    }

    /**
     * Returns the latest known version constant.
     */
    public static MinecraftVersion latest() {
        MinecraftVersion[] vals = values();
        return vals[vals.length - 1];
    }

    @Override
    public String toString() {
        return displayName + " (protocol=" + protocolId + ")";
    }
}

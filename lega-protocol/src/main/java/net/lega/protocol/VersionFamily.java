package net.lega.protocol;

/**
 * Groups of closely related Minecraft versions that share
 * the same {@link VersionAdapter} implementation.
 *
 * @author maatsuh
 * @since  1.0.0
 */
public enum VersionFamily {

    /** 1.7.x — original Netty rewrite era. */
    LEGACY("Legacy (1.7.x)"),

    /** 1.8.x — bountiful update, PvP meta, bungeecord era. */
    PRE_NETTY("Bountiful (1.8.x)"),

    /** 1.9 / 1.9.4 — combat update, dual-wield. */
    COMBAT_UPDATE("Combat Update (1.9.x)"),

    /** 1.10 — Frostburn update. */
    FROSTBURN("Frostburn (1.10.x)"),

    /** 1.11 / 1.11.2 — Exploration update. */
    EXPLORATION("Exploration Update (1.11.x)"),

    /** 1.12.x — World of Color update. */
    WORLD_OF_COLOR("World of Color (1.12.x)"),

    /** 1.13.x — Update Aquatic; major flattening of block data. */
    UPDATE_AQUATIC("Update Aquatic (1.13.x)"),

    /** 1.14.x — Village & Pillage. */
    VILLAGE_AND_PILLAGE("Village & Pillage (1.14.x)"),

    /** 1.15.x — Buzzy Bees. */
    BUZZY_BEES("Buzzy Bees (1.15.x)"),

    /** 1.16.x — Nether Update. */
    NETHER_UPDATE("Nether Update (1.16.x)"),

    /** 1.17 / 1.17.1 — Caves & Cliffs part I. */
    CAVES_CLIFFS_1("Caves & Cliffs I (1.17.x)"),

    /** 1.18.x — Caves & Cliffs part II; world height expanded to 384. */
    CAVES_CLIFFS_2("Caves & Cliffs II (1.18.x)"),

    /** 1.19.x — The Wild Update; chat signing. */
    WILD_UPDATE("The Wild Update (1.19.x)"),

    /** 1.20.x — Trails & Tales; archaeology, camels, item components. */
    TRAILS_TALES("Trails & Tales (1.20.x)"),

    /** 1.21.x — Tricky Trials; vault blocks, wind charges. */
    TRICKY_TRIALS("Tricky Trials (1.21.x)");

    private final String displayName;

    VersionFamily(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

package net.lega.server.config;

/**
 * Manages the standard Minecraft {@code eula.txt} file for operators that
 * prefer the conventional format, in addition to the versions.yml acceptance.
 *
 * <p>The server respects <em>either</em> eula.txt OR the accept-eula flag in
 * versions.yml.  If either is {@code true} the EULA is considered accepted.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public final class EulaConfig {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/EULA");
    private static final String FILE_NAME = "eula.txt";

    private final Path filePath;
    private boolean accepted = false;

    public EulaConfig(Path serverRoot) {
        this.filePath = serverRoot.resolve(FILE_NAME);
    }

    /**
     * Reads (or creates) {@code eula.txt}.
     *
     * @return {@code true} if the operator has set {@code eula=true}
     * @throws IOException if the file cannot be read or written
     */
    public boolean load() throws IOException {
        if (!Files.exists(filePath)) {
            writeDefaultEula();
            // File will be synced via markAccepted() — no user action needed
            accepted = false;
            return false;
        }

        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            props.load(r);
        }

        accepted = Boolean.parseBoolean(props.getProperty("eula", "false").trim());
        return accepted;
    }

    /**
     * Marks the EULA as accepted and persists it to disk.
     * Called if the operator accepted via versions.yml and eula.txt is still false.
     */
    public void markAccepted() throws IOException {
        accepted = true;
        String ts = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        String content = "#By changing the setting below to TRUE you are indicating your agreement\n"
                + "#to the EULA (https://aka.ms/MinecraftEULA).\n"
                + "#" + ts + "\n"
                + "eula=true\n";
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        LOGGER.info("eula.txt updated — EULA accepted.");
    }

    public boolean isAccepted() {
        return accepted;
    }

    // ── private ────────────────────────────────────────────────────────────────

    private void writeDefaultEula() throws IOException {
        Files.createDirectories(filePath.getParent());
        String ts = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        String content = "#By changing the setting below to TRUE you are indicating your agreement\n"
                + "#to the EULA (https://aka.ms/MinecraftEULA).\n"
                + "#" + ts + "\n"
                + "eula=false\n";
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }
}

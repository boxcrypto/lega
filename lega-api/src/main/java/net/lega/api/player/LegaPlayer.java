package net.lega.api.player;

import net.lega.api.event.LegaEvent;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a connected player on the LEGA server.
 *
 * <p>All methods that mutate world state (teleport, send messages, etc.)
 * are safe to call from any thread — they are internally dispatched to
 * the main tick thread where required.</p>
 *
 * @author maatsuh
 * @since  1.0.0
 */
public interface LegaPlayer {

    // ── identity ──────────────────────────────────────────────────────────

    /** Returns the player's unique Mojang UUID. */
    UUID getUniqueId();

    /** Returns the player's current username. */
    String getName();

    /** Returns the player's display name (may include formatting). */
    String getDisplayName();

    /** Sets the player's display name. */
    void setDisplayName(String displayName);

    // ── connection ────────────────────────────────────────────────────────

    /** Returns the remote address of this connection. */
    InetSocketAddress getAddress();

    /** Returns the detected Minecraft protocol version number. */
    int getProtocolVersion();

    /**
     * Returns the canonical Minecraft version string (e.g. {@code "1.21.4"},
     * {@code "1.8.9"}) for this player's client, if recognised.
     * Returns an empty optional for protocol versions not enumerated by the
     * protocol layer.
     */
    Optional<String> getMinecraftVersion();

    /** Returns the average network latency in milliseconds (ping). */
    int getPing();

    /** Returns whether this player is still connected (not yet disconnected). */
    boolean isOnline();

    /** Returns whether this player passed Velocity modern forwarding validation. */
    boolean isVelocityVerified();

    // ── messaging ─────────────────────────────────────────────────────────

    /**
     * Sends a chat message to this player.
     *
     * @param message plain text or JSON text component
     */
    void sendMessage(String message);

    /**
     * Sends a chat message using a translated key (1.11+).
     * Falls back to the key string on older versions.
     */
    void sendTranslated(String translateKey, Object... args);

    /**
     * Sends an action bar message.
     * Falls back to chat on versions that don't support action bar.
     *
     * @param message text to display above the hotbar
     */
    void sendActionBar(String message);

    /**
     * Sends a title screen to this player.
     *
     * @param title    main title, or null to skip
     * @param subtitle subtitle, or null to skip
     * @param fadeIn   fade-in ticks
     * @param stay     stay ticks
     * @param fadeOut  fade-out ticks
     */
    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /**
     * Clears any currently displayed title.
     */
    void clearTitle();

    // ── permissions ───────────────────────────────────────────────────────

    /** Returns whether this player has the given permission node. */
    boolean hasPermission(String permission);

    /** Returns whether this player is an operator. */
    boolean isOp();

    /** Grants or revokes operator status. */
    void setOp(boolean op);

    // ── world / position ─────────────────────────────────────────────────

    /** Returns the name of the world this player is currently in. */
    String getWorldName();

    /** Returns the current X coordinate. */
    double getX();

    /** Returns the current Y coordinate. */
    double getY();

    /** Returns the current Z coordinate. */
    double getZ();

    /** Returns the player's current yaw (horizontal rotation). */
    float getYaw();

    /** Returns the player's current pitch (vertical rotation). */
    float getPitch();

    /**
     * Teleports the player to the specified location asynchronously.
     *
     * @return a future that completes once the teleport is acknowledged
     */
    CompletableFuture<Boolean> teleport(double x, double y, double z, float yaw, float pitch);

    /**
     * Teleports the player to another world instance.
     *
     * @param instanceName target world instance name
     * @param x,y,z spawn position within that world
     */
    CompletableFuture<Boolean> teleportToInstance(String instanceName, double x, double y, double z);

    // ── game state ────────────────────────────────────────────────────────

    /** Returns the player's current game mode (0=survival,1=creative,2=adventure,3=spectator). */
    int getGameMode();

    /** Sets the player's game mode. */
    void setGameMode(int gameMode);

    /** Returns the player's health (0.0 to 20.0). */
    float getHealth();

    /** Sets the player's health. Values ≤ 0 will trigger death. */
    void setHealth(float health);

    /** Returns the player's food level (0–20). */
    int getFoodLevel();

    /** Sets the player's food level. */
    void setFoodLevel(int foodLevel);

    /** Returns the player's current experience level. */
    int getExpLevel();

    /** Sets the player's XP level. */
    void setExpLevel(int level);

    /** Returns whether this player is sneaking. */
    boolean isSneaking();

    /** Returns whether this player is sprinting. */
    boolean isSprinting();

    // ── actions ───────────────────────────────────────────────────────────

    /**
     * Kicks this player with the given reason.
     *
     * @param reason plain text reason shown on the disconnect screen
     */
    void kick(String reason);

    /**
     * Executes a command as this player.
     *
     * @param command command string (without the leading {@code /})
     */
    void performCommand(String command);

    /**
     * Returns the player's current locale as a BCP-47 language tag
     * (e.g. {@code "en_us"}, {@code "pt_br"}).
     */
    String getLocale();

    /**
     * Returns an opaque metadata map for attaching plugin-specific
     * data to this player object.
     *
     * <p>Keys should be namespaced strings (e.g. {@code "myplugin:arena-id"})
     * to avoid collisions between plugins.</p>
     */
    java.util.Map<String, Object> getMetadata();
}

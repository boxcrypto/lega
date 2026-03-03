package net.lega.security;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;


public record SecurityEvent(
        @NotNull Type type,
        @NotNull String ip,
        @NotNull String detail,
        long timestampMs
) {
    public enum Type {
        PACKET_EXPLOIT(true),
        RATE_LIMIT(false),
        BOT_DETECTED(true),
        FLOOD_DETECTED(true),
        IP_BLOCKED(true),
        BOOK_BAN_ATTEMPT(true),
        CHUNK_EXPLOIT(true),
        SUSPICIOUS_MOVEMENT(false);

        private final boolean critical;

        Type(boolean critical) {
            this.critical = critical;
        }

        public boolean isCritical() {
            return critical;
        }
    }
}

package net.lega.security;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class SecurityDecision {

    private static final SecurityDecision ALLOW = new SecurityDecision(false, null);

    private final boolean blocked;
    private final String reason;

    private SecurityDecision(boolean blocked, @Nullable String reason) {
        this.blocked = blocked;
        this.reason  = reason;
    }

    public static SecurityDecision allow() {
        return ALLOW;
    }

    public static SecurityDecision block(@NotNull String reason) {
        return new SecurityDecision(true, reason);
    }

    public boolean isAllowed()  { return !blocked; }
    public boolean isBlocked()  { return blocked; }

    @Nullable
    public String getReason()   { return reason; }

    @Override
    public String toString() {
        return blocked ? "BLOCK(" + reason + ")" : "ALLOW";
    }
}

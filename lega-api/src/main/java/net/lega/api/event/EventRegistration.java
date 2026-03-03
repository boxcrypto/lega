package net.lega.api.event;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;


public interface EventRegistration {

    
    @NotNull
    Class<? extends LegaEvent> getEventClass();

    
    @NotNull
    EventPriority getPriority();

    /** Removes this handler from the event bus. Idempotent. */
    void unregister();

    /** Returns {@code true} if this registration is still active. */
    boolean isActive();
}
    
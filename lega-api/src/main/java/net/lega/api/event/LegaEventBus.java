package net.lega.api.event;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


public interface LegaEventBus {

    
    @NotNull
    <T extends LegaEvent> EventRegistration register(
            @NotNull Class<T> eventClass,
            @NotNull EventPriority priority,
            @NotNull LegaEventHandler<T> handler
    );

    
    @NotNull
    default <T extends LegaEvent> EventRegistration register(
            @NotNull Class<T> eventClass,
            @NotNull LegaEventHandler<T> handler
    ) {
        return register(eventClass, EventPriority.NORMAL, handler);
    }

    
    @NotNull
    <T extends LegaEvent> T fire(@NotNull T event);

    
    @NotNull
    <T extends LegaEvent> java.util.concurrent.CompletableFuture<T> fireAsync(@NotNull T event);

    /**
     * Registers all {@link Subscribe @Subscribe}-annotated methods found on
     * the given listener object.
     *
     * @param listener the listener to scan; must not be {@code null}
     */
    void registerAll(@NotNull Object listener);

    /**
     * Removes a handler registration previously returned by {@link #register}.
     * Idempotent — calling with an already-unregistered handle is safe.
     *
     * @param registration the registration to remove
     */
    void unregister(@NotNull EventRegistration registration);

    /**
     * Removes all registrations that were created for the given listener
     * instance via {@link #registerAll}.
     *
     * @param listener the listener whose handlers should be removed
     */
    void unregisterAll(@NotNull Object listener);

    /**
     * Returns the total number of active handler registrations across all
     * event types.
     */
    int handlerCount();

    
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Subscribe {
        EventPriority priority() default EventPriority.NORMAL;
        boolean ignoreCancelled() default false;
    }
}

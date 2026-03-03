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

    
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Subscribe {
        EventPriority priority() default EventPriority.NORMAL;
        boolean ignoreCancelled() default false;
    }
}

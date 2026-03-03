package net.lega.api.event;

/**
 * @author maatsuh
 * @since  1.0.0
 */


@FunctionalInterface
public interface LegaEventHandler<T extends LegaEvent> {

    /**
     * Handles an event.
     *
     * @param event the event to handle; never {@code null}
     */
    void handle(T event);
}
    
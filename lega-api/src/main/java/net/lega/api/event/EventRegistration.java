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

    
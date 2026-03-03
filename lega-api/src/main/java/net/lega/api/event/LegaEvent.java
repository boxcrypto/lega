package net.lega.api.event;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;


public abstract class LegaEvent {

    private final boolean async;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final long timestamp;

    protected LegaEvent() {
        this(false);
    }

    protected LegaEvent(boolean async) {
        this.async = async;
        this.timestamp = System.nanoTime();
    }

    
    public boolean isAsync() {
        return async;
    }

    
    public long getTimestamp() {
        return timestamp;
    }

    
    public boolean isCancellable() {
        return false;
    }

    
    public boolean isCancelled() {
        return cancelled.get();
    }

    
    public void setCancelled(boolean cancel) {
        if (!isCancellable()) {
            throw new UnsupportedOperationException(
                    "Event " + getClass().getSimpleName() + " is not cancellable!"
            );
        }
        cancelled.set(cancel);
    }

    
    @NotNull
    public String getEventName() {
        return getClass().getSimpleName();
    }
}

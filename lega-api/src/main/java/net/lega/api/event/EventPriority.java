package net.lega.api.event;

/**
 * @author maatsuh
 * @since  1.0.0
 */


public enum EventPriority {

    /** Lowest priority — receives the event last. */
    LOWEST(0),

    /** Below normal priority. */
    LOW(1),

    /** Default priority. */
    NORMAL(2),

    /** Above normal priority. */
    HIGH(3),

    /** Highest priority — receives the event first. */
    HIGHEST(4),

    /**
     * Monitor priority — for observation only.
     * Handlers at this priority should never modify the event.
     */
    MONITOR(5);

    private final int ordinalValue;

    EventPriority(int ordinalValue) {
        this.ordinalValue = ordinalValue;
    }

    public int getOrdinalValue() {
        return ordinalValue;
    }
}

package net.lega.api.scheduler;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;


public interface TaskHandle {

    /** Cancels this task. Idempotent. */
    void cancel();

    /** Returns {@code true} if this task has been cancelled. */
    boolean isCancelled();

    /** Returns {@code true} if this task has finished executing (done or cancelled). */
    boolean isDone();

    /** Returns {@code true} if the task is currently in the process of running. */
    boolean isRunning();

    /** Returns {@code true} if this task fires repeatedly on a fixed period. */
    boolean isRepeating();

    /** Returns {@code true} if this task runs on the main server thread. */
    boolean isSync();

    /** Returns the internal numeric ID assigned to this task. */
    long getTaskId();

    /** Number of times this task has been executed. */
    long getExecutionCount();

    /** Average execution time in nanoseconds. Returns {@code -1} if not yet executed. */
    long getAverageExecutionTimeNanos();
}
    
package net.lega.async;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.api.scheduler.TaskHandle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


final class LegaTaskHandle implements TaskHandle {

    private final long taskId;
    private final boolean isSync;
    private final boolean isRepeating;

    private final AtomicBoolean cancelled  = new AtomicBoolean(false);
    private final AtomicBoolean running    = new AtomicBoolean(false);
    private final AtomicLong executionCount = new AtomicLong(0);
    private final AtomicLong totalExecTimeMs = new AtomicLong(0);

    LegaTaskHandle(long taskId, boolean isSync, boolean isRepeating) {
        this.taskId = taskId;
        this.isSync = isSync;
        this.isRepeating = isRepeating;
    }

    @Override
    public long getTaskId() { return taskId; }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public boolean isCancelled() { return cancelled.get(); }

    @Override
    public boolean isDone() {
        return cancelled.get() || (!isRepeating && executionCount.get() > 0 && !running.get());
    }

    @Override
    public boolean isRepeating() { return isRepeating; }

    @Override
    public boolean isSync() { return isSync; }

    @Override
    public void cancel() { cancelled.set(true); }

    @Override
    public long getExecutionCount() { return executionCount.get(); }

    @Override
    public long getAverageExecutionTimeNanos() {
        long count = executionCount.get();
        if (count == 0) return -1;
        return (totalExecTimeMs.get() * 1_000_000) / count;
    }

    void markExecuted(long durationMs) {
        executionCount.incrementAndGet();
        totalExecTimeMs.addAndGet(durationMs);
        running.set(false);
    }

    void markRunning() {
        running.set(true);
    }
}

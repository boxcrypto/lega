package net.lega.async;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.api.scheduler.LegaScheduler;
import net.lega.api.scheduler.TaskHandle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;


public final class LegaAsyncEngine implements LegaScheduler {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Async");

    private final Thread mainThread;
    private final ForkJoinPool cpuPool;
    private final ExecutorService ioPool;
    private final ScheduledThreadPoolExecutor scheduledPool;

    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Pending tasks on main thread (thread-safe)
    private final ConcurrentLinkedQueue<PendingTask> mainThreadQueue = new ConcurrentLinkedQueue<>();

    public LegaAsyncEngine() {
        this.mainThread = Thread.currentThread();

        int processors = Runtime.getRuntime().availableProcessors();
        this.cpuPool = new ForkJoinPool(
                processors,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                (thread, exc) -> LOGGER.error("[LEGA/Async] Uncaught exception in CPU pool", exc),
                true // async mode
        );

        // Java 21 Virtual Threads for I/O
        this.ioPool = Executors.newVirtualThreadPerTaskExecutor();

        this.scheduledPool = new ScheduledThreadPoolExecutor(2);
        this.scheduledPool.setThreadFactory(r -> {
            Thread t = new Thread(r, "LEGA-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.scheduledPool.setRemoveOnCancelPolicy(true);

        LOGGER.info("[LEGA/Async] Initialized. CPU pool: {} threads, I/O: Virtual Threads (Project Loom)", processors);
    }

    
    public void tickMainThread() {
        if (!isMainThread()) {
            LOGGER.warn("[LEGA/Async] tickMainThread() called from non-main thread!");
            return;
        }
        PendingTask task;
        while ((task = mainThreadQueue.poll()) != null) {
            if (!task.handle.isCancelled()) {
                long start = System.nanoTime();
                try {
                    task.runnable.run();
                } catch (Exception e) {
                    LOGGER.error("[LEGA/Async] Exception in main thread task #{}", task.handle.getTaskId(), e);
                }
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                if (durationMs > 5) {
                    LOGGER.warn("[LEGA/Async] Main-thread task #{} blocked for {}ms! Consider async.",
                            task.handle.getTaskId(), durationMs);
                }
                task.handle.markExecuted(durationMs);
            }
        }
    }

    // =========================================================================
    // LegaScheduler implementation
    // =========================================================================

    @Override
    public TaskHandle runSync(Runnable task) {
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), true, false);
        mainThreadQueue.add(new PendingTask(task, handle));
        return handle;
    }

    @Override
    public TaskHandle runSyncLater(Runnable task, Duration delay) {
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), true, false);
        scheduledPool.schedule(() -> mainThreadQueue.add(new PendingTask(task, handle)),
                delay.toMillis(), TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public TaskHandle runSyncTimer(Runnable task, Duration delay, Duration period) {
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), true, true);
        scheduledPool.scheduleAtFixedRate(() -> {
            if (!handle.isCancelled()) {
                mainThreadQueue.add(new PendingTask(task, handle));
            }
        }, delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), false, false);
        ioPool.submit(() -> {
            if (!handle.isCancelled()) {
                long start = System.nanoTime();
                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.error("[LEGA/Async] Exception in async task #{}", handle.getTaskId(), e);
                }
                handle.markExecuted((System.nanoTime() - start) / 1_000_000);
            }
        });
        return handle;
    }

    @Override
    public TaskHandle runAsyncLater(Runnable task, Duration delay) {
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), false, false);
        scheduledPool.schedule(() -> {
            if (!handle.isCancelled()) {
                ioPool.submit(task);
                handle.markExecuted(0);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public TaskHandle runAsyncTimer(Runnable task, Duration delay, Duration period) {
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), false, true);
        scheduledPool.scheduleAtFixedRate(() -> {
            if (!handle.isCancelled()) {
                ioPool.submit(task);
                handle.markExecuted(0);
            }
        }, delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public TaskHandle runSyncLaterTicks(Runnable task, long ticks) {
        return runSyncLater(task, Duration.ofMillis(ticks * 50L));
    }

    @Override
    public TaskHandle runSyncTimerTicks(Runnable task, long delayTicks, long periodTicks) {
        return runSyncTimer(task, Duration.ofMillis(delayTicks * 50L), Duration.ofMillis(periodTicks * 50L));
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, ioPool);
    }

    @Override
    public CompletableFuture<Void> runOnMainThread(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        LegaTaskHandle handle = new LegaTaskHandle(nextId(), true, false);
        mainThreadQueue.add(new PendingTask(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, handle));
        return future;
    }

    @Override
    public ExecutorService getIOExecutor() {
        return ioPool;
    }

    @Override
    public ExecutorService getCPUExecutor() {
        return cpuPool;
    }

    @Override
    public int getPendingTaskCount() {
        return mainThreadQueue.size() + cpuPool.getQueuedSubmissionCount();
    }

    @Override
    public boolean isMainThread() {
        return Thread.currentThread() == mainThread;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    
    public void shutdown() {
        running.set(false);
        scheduledPool.shutdownNow();
        cpuPool.shutdown();
        ioPool.shutdown();
        LOGGER.info("[LEGA/Async] Async engine shut down.");
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private long nextId() {
        return taskIdCounter.incrementAndGet();
    }

    private record PendingTask(Runnable runnable, LegaTaskHandle handle) {}
}

package net.lega.api.scheduler;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;


public interface LegaScheduler {

    // =========================================================================
    // Synchronous (main-thread) scheduling
    // =========================================================================

    
    @NotNull
    TaskHandle runSync(@NotNull Runnable task);

    
    @NotNull
    TaskHandle runSyncLater(@NotNull Runnable task, @NotNull Duration delay);

    
    @NotNull
    TaskHandle runSyncTimer(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period);

    // =========================================================================
    // Asynchronous scheduling
    // =========================================================================

    
    @NotNull
    TaskHandle runAsync(@NotNull Runnable task);

    
    @NotNull
    TaskHandle runAsyncLater(@NotNull Runnable task, @NotNull Duration delay);

    
    @NotNull
    TaskHandle runAsyncTimer(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period);

    // =========================================================================
    // Tick-aligned scheduling
    // =========================================================================

    
    @NotNull
    TaskHandle runSyncLaterTicks(@NotNull Runnable task, long ticks);

    
    @NotNull
    TaskHandle runSyncTimerTicks(@NotNull Runnable task, long delayTicks, long periodTicks);

    // =========================================================================
    // CompletableFuture API
    // =========================================================================

    
    @NotNull
    <T> CompletableFuture<T> supplyAsync(@NotNull java.util.function.Supplier<T> supplier);

    
    @NotNull
    CompletableFuture<Void> runOnMainThread(@NotNull Runnable action);

    // =========================================================================
    // Thread pool access
    // =========================================================================

    
    @NotNull
    ExecutorService getIOExecutor();

    
    @NotNull
    ExecutorService getCPUExecutor();

    
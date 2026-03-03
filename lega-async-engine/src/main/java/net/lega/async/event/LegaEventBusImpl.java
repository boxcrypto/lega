package net.lega.async.event;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.api.event.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public final class LegaEventBusImpl implements LegaEventBus {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/EventBus");

    // eventClass → sorted list of handlers (by priority)
    private final ConcurrentHashMap<Class<? extends LegaEvent>, List<HandlerEntry<?>>> handlers =
            new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor;
    private final AtomicInteger totalHandlerCount = new AtomicInteger(0);

    public LegaEventBusImpl(ExecutorService asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LegaEvent> EventRegistration register(
            Class<T> eventClass,
            EventPriority priority,
            LegaEventHandler<T> handler) {

        LegaEventRegistration registration = new LegaEventRegistration(eventClass, priority);

        handlers.compute(eventClass, (k, existing) -> {
            List<HandlerEntry<?>> list = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            list.add(new HandlerEntry<>(handler, priority, registration));
            list.sort(Comparator.comparingInt(e -> e.priority().getOrdinalValue()));
            totalHandlerCount.incrementAndGet();
            return Collections.unmodifiableList(list);
        });

        return registration;
    }

    @Override
    public void registerAll(Object listener) {
        Class<?> cls = listener.getClass();
        for (Method method : cls.getDeclaredMethods()) {
            LegaEventBus.Subscribe annotation = method.getAnnotation(LegaEventBus.Subscribe.class);
            if (annotation == null) continue;

            if (method.getParameterCount() != 1) {
                LOGGER.warn("[LEGA/EventBus] @Subscribe method {} must have exactly one parameter!", method.getName());
                continue;
            }

            Class<?> paramType = method.getParameterTypes()[0];
            if (!LegaEvent.class.isAssignableFrom(paramType)) {
                LOGGER.warn("[LEGA/EventBus] @Subscribe method {} parameter must extend LegaEvent!", method.getName());
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<LegaEvent> eventClass = (Class<LegaEvent>) paramType;
            method.setAccessible(true);

            LegaEventHandler<LegaEvent> handler = event -> {
                try {
                    method.invoke(listener, event);
                } catch (Exception e) {
                    LOGGER.error("[LEGA/EventBus] Exception in @Subscribe handler {}#{}", cls.getSimpleName(), method.getName(), e);
                }
            };

            register(eventClass, annotation.priority(), handler);
        }
    }

    @Override
    public void unregister(EventRegistration registration) {
        if (!(registration instanceof LegaEventRegistration reg)) return;
        reg.unregister();
        handlers.compute(reg.getEventClass(), (k, existing) -> {
            if (existing == null) return null;
            List<HandlerEntry<?>> newList = new ArrayList<>(existing);
            newList.removeIf(e -> e.registration() == reg);
            totalHandlerCount.decrementAndGet();
            return Collections.unmodifiableList(newList);
        });
    }

    @Override
    public void unregisterAll(Object listener) {
        // Track by listener instance via WeakReference or object identity
        // Simplified: remove all handlers whose underlying listener matches
        handlers.replaceAll((cls, list) -> {
            List<HandlerEntry<?>> filtered = list.stream()
                    .filter(e -> e.registration().isActive())
                    .toList();
            int removed = list.size() - filtered.size();
            totalHandlerCount.addAndGet(-removed);
            return Collections.unmodifiableList(filtered);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LegaEvent> T fire(T event) {
        List<HandlerEntry<?>> handlerList = handlers.get(event.getClass());
        if (handlerList == null || handlerList.isEmpty()) return event;

        for (HandlerEntry<?> entry : handlerList) {
            if (!entry.registration().isActive()) continue;
            if (event.isCancellable() && event.isCancelled()) {
                // Continue to MONITOR priority even when cancelled
                if (entry.priority() != EventPriority.MONITOR) continue;
            }
            try {
                ((HandlerEntry<T>) entry).handler().handle(event);
            } catch (Exception e) {
                LOGGER.error("[LEGA/EventBus] Exception dispatching event {} to handler",
                        event.getEventName(), e);
            }
        }
        return event;
    }

    @Override
    public <T extends LegaEvent> CompletableFuture<T> fireAsync(T event) {
        return CompletableFuture.supplyAsync(() -> fire(event), asyncExecutor);
    }

    @Override
    public int handlerCount() {
        return totalHandlerCount.get();
    }

    // =========================================================================
    // Internal records
    // =========================================================================

    private record HandlerEntry<T extends LegaEvent>(
            LegaEventHandler<T> handler,
            EventPriority priority,
            LegaEventRegistration registration
    ) {}

    private static final class LegaEventRegistration implements EventRegistration {
        private final Class<? extends LegaEvent> eventClass;
        private final EventPriority priority;
        private volatile boolean active = true;

        LegaEventRegistration(Class<? extends LegaEvent> eventClass, EventPriority priority) {
            this.eventClass = eventClass;
            this.priority   = priority;
        }

        @Override public Class<? extends LegaEvent> getEventClass() { return eventClass; }
        @Override public EventPriority getPriority() { return priority; }
        @Override public boolean isActive() { return active; }
        @Override public void unregister() { active = false; }
    }
}

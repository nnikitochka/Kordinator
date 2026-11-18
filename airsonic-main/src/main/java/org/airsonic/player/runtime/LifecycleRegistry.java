package org.airsonic.player.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Framework-neutral registry for application lifecycle events and resources.
 * Manages ordered startup and graceful shutdown with timeouts and exception safety.
 */
public class LifecycleRegistry implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(LifecycleRegistry.class);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    public record LifecycleItem(String name, int order, AutoCloseable closeable) {}
    public record StartupItem(String name, int order, Runnable action) {}

    private final List<StartupItem> startupItems = new CopyOnWriteArrayList<>();
    private final List<LifecycleItem> shutdownItems = new CopyOnWriteArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean shutdownComplete = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Register a startup callback.
     * Actions are executed in ascending order during {@link #start()}.
     */
    public void registerStartup(String name, Runnable action) {
        registerStartup(name, 0, action);
    }

    public void registerStartup(String name, int order, Runnable action) {
        if (action == null) {
            return;
        }
        startupItems.add(new StartupItem(name, order, action));
        LOG.debug("Registered lifecycle startup action: {} (order {})", name, order);
    }

    /**
     * Register a resource or component for managed shutdown.
     * Components are shut down in reverse order of registration (LIFO) or by descending order.
     */
    public void registerShutdown(String name, AutoCloseable closeable) {
        registerShutdown(name, 0, closeable);
    }

    public void registerShutdown(String name, int order, AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        shutdownItems.add(new LifecycleItem(name, order, closeable));
        LOG.debug("Registered lifecycle shutdown handler: {} (order {})", name, order);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public boolean isShutdownComplete() {
        return shutdownComplete.get();
    }

    public List<LifecycleItem> getShutdownItems() {
        return Collections.unmodifiableList(shutdownItems);
    }

    public List<StartupItem> getStartupItems() {
        return Collections.unmodifiableList(startupItems);
    }

    /**
     * Run all registered startup callbacks in ascending order.
     */
    public void start() {
        if (shuttingDown.get() || !started.compareAndSet(false, true)) {
            throw new IllegalStateException("Lifecycle has already started or is shutting down");
        }
        LOG.info("Running {} startup lifecycle items...", startupItems.size());
        List<StartupItem> sorted = new ArrayList<>(startupItems);
        sorted.sort(Comparator.comparingInt(StartupItem::order));
        for (StartupItem item : sorted) {
            try {
                LOG.info("Executing startup action: {}", item.name());
                item.action().run();
            } catch (RuntimeException | Error t) {
                LOG.error("Error executing startup action '{}'", item.name(), t);
                shutdown();
                throw t;
            }
        }
    }

    /**
     * Gracefully shutdown all registered components using default timeout.
     */
    public void shutdown() {
        shutdown(DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Gracefully shutdown all registered components within the given timeout.
     * Shutdown occurs in reverse order of registration (LIFO) for items of equal priority,
     * and higher order values are shut down first.
     */
    public void shutdown(Duration totalTimeout) {
        if (!shuttingDown.compareAndSet(false, true)) {
            LOG.warn("LifecycleRegistry shutdown already in progress or completed");
            return;
        }

        LOG.info("Initiating graceful shutdown for {} components (timeout {})...", shutdownItems.size(), totalTimeout);

        List<LifecycleItem> shutdownList = new ArrayList<>(shutdownItems);
        Collections.reverse(shutdownList);
        shutdownList.sort((a, b) -> Integer.compare(b.order(), a.order()));

        long deadline = System.currentTimeMillis() + totalTimeout.toMillis();

        for (LifecycleItem item : shutdownList) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                LOG.warn("Shutdown timeout exceeded before shutting down: {}", item.name());
                break;
            }
            try {
                LOG.info("Shutting down component: {}", item.name());
                item.closeable().close();
            } catch (Throwable t) {
                LOG.error("Error shutting down component '{}'", item.name(), t);
            }
        }

        shutdownItems.clear();
        shutdownComplete.set(true);
        LOG.info("LifecycleRegistry graceful shutdown complete.");
    }

    @Override
    public void close() {
        shutdown();
    }
}

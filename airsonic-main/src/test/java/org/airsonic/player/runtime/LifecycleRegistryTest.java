package org.airsonic.player.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleRegistryTest {

    @Test
    void runsStartupCallbacksInAscendingOrder() {
        LifecycleRegistry registry = new LifecycleRegistry();
        List<String> executionOrder = new ArrayList<>();

        registry.registerStartup("second", 10, () -> executionOrder.add("second"));
        registry.registerStartup("first", 1, () -> executionOrder.add("first"));
        registry.registerStartup("third", 20, () -> executionOrder.add("third"));

        registry.start();

        assertEquals(List.of("first", "second", "third"), executionOrder);
    }

    @Test
    void startupContinuesEvenIfOneActionThrows() {
        LifecycleRegistry registry = new LifecycleRegistry();
        List<String> executionOrder = new ArrayList<>();

        registry.registerStartup("ok-1", 1, () -> executionOrder.add("ok-1"));
        registry.registerStartup("failing", 2, () -> { throw new RuntimeException("boom"); });
        registry.registerStartup("ok-2", 3, () -> executionOrder.add("ok-2"));

        assertDoesNotThrow(() -> registry.start());
        assertEquals(List.of("ok-1", "ok-2"), executionOrder);
    }

    @Test
    void runsShutdownCallbacksInLifoOrderForSamePriority() {
        LifecycleRegistry registry = new LifecycleRegistry();
        List<String> shutdownOrder = new ArrayList<>();

        registry.registerShutdown("resource-1", () -> shutdownOrder.add("resource-1"));
        registry.registerShutdown("resource-2", () -> shutdownOrder.add("resource-2"));
        registry.registerShutdown("resource-3", () -> shutdownOrder.add("resource-3"));

        registry.shutdown();

        // LIFO order: resource-3, resource-2, resource-1
        assertEquals(List.of("resource-3", "resource-2", "resource-1"), shutdownOrder);
        assertTrue(registry.isShuttingDown());
        assertTrue(registry.isShutdownComplete());
    }

    @Test
    void runsHigherOrderShutdownBeforeLowerOrder() {
        LifecycleRegistry registry = new LifecycleRegistry();
        List<String> shutdownOrder = new ArrayList<>();

        registry.registerShutdown("low-priority", 1, () -> shutdownOrder.add("low-priority"));
        registry.registerShutdown("high-priority", 100, () -> shutdownOrder.add("high-priority"));
        registry.registerShutdown("mid-priority", 50, () -> shutdownOrder.add("mid-priority"));

        registry.shutdown();

        assertEquals(List.of("high-priority", "mid-priority", "low-priority"), shutdownOrder);
    }

    @Test
    void shutdownContinuesEvenIfOneCallbackThrows() {
        LifecycleRegistry registry = new LifecycleRegistry();
        AtomicBoolean secondClosed = new AtomicBoolean(false);

        registry.registerShutdown("first-closed", () -> secondClosed.set(true));
        registry.registerShutdown("failing", () -> { throw new RuntimeException("error on close"); });

        assertDoesNotThrow(() -> registry.shutdown());
        assertTrue(secondClosed.get());
        assertTrue(registry.isShutdownComplete());
    }

    @Test
    void shutdownIsIdempotent() {
        LifecycleRegistry registry = new LifecycleRegistry();
        AtomicInteger closeCount = new AtomicInteger();

        registry.registerShutdown("resource", closeCount::incrementAndGet);

        registry.shutdown();
        registry.shutdown();
        registry.close();

        assertEquals(1, closeCount.get());
    }
}

package org.airsonic.player.runtime

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LifecycleRegistryTest {
    @Test
    fun runsStartupCallbacksInAscendingOrder() {
        val registry = LifecycleRegistry()
        val order = mutableListOf<String>()
        registry.registerStartup("second", 10) { order.add("second") }
        registry.registerStartup("first", 1) { order.add("first") }
        registry.registerStartup("third", 20) { order.add("third") }
        registry.start()
        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun startupFailureClosesResourcesAndStopsFurtherActions() {
        val registry = LifecycleRegistry()
        val order = mutableListOf<String>()
        registry.registerShutdown("first") { order.add("close-1") }
        registry.registerShutdown("second") { order.add("close-2") }
        registry.registerStartup("ok-1", 1) { order.add("ok-1") }
        registry.registerStartup("failing", 2) { throw RuntimeException("boom") }
        registry.registerStartup("ok-2", 3) { order.add("ok-2") }

        assertThrows(RuntimeException::class.java) { registry.start() }
        assertEquals(listOf("ok-1", "close-2", "close-1"), order)
        assertTrue(registry.isShutdownComplete())
        assertThrows(IllegalStateException::class.java) { registry.start() }
    }

    @Test
    fun startupCannotRunTwiceOrAfterShutdown() {
        val registry = LifecycleRegistry()
        val count = AtomicInteger()
        registry.registerStartup("once") { count.incrementAndGet(); Unit }
        registry.start()
        assertThrows(IllegalStateException::class.java) { registry.start() }
        registry.shutdown()
        assertThrows(IllegalStateException::class.java) { registry.start() }
        assertEquals(1, count.get())
    }

    @Test
    fun runsShutdownCallbacksInLifoOrderForSamePriority() {
        val registry = LifecycleRegistry()
        val order = mutableListOf<String>()
        registry.registerShutdown("resource-1") { order.add("resource-1") }
        registry.registerShutdown("resource-2") { order.add("resource-2") }
        registry.registerShutdown("resource-3") { order.add("resource-3") }
        registry.shutdown()
        assertEquals(listOf("resource-3", "resource-2", "resource-1"), order)
        assertTrue(registry.isShuttingDown())
        assertTrue(registry.isShutdownComplete())
    }

    @Test
    fun runsHigherOrderShutdownBeforeLowerOrder() {
        val registry = LifecycleRegistry()
        val order = mutableListOf<String>()
        registry.registerShutdown("low-priority", 1) { order.add("low-priority") }
        registry.registerShutdown("high-priority", 100) { order.add("high-priority") }
        registry.registerShutdown("mid-priority", 50) { order.add("mid-priority") }
        registry.shutdown()
        assertEquals(listOf("high-priority", "mid-priority", "low-priority"), order)
    }

    @Test
    fun shutdownContinuesEvenIfOneCallbackThrows() {
        val registry = LifecycleRegistry()
        val secondClosed = AtomicBoolean(false)
        registry.registerShutdown("first-closed") { secondClosed.set(true) }
        registry.registerShutdown("failing") { throw RuntimeException("error on close") }
        assertDoesNotThrow { registry.shutdown() }
        assertTrue(secondClosed.get())
        assertTrue(registry.isShutdownComplete())
    }

    @Test
    fun shutdownIsIdempotent() {
        val registry = LifecycleRegistry()
        val closeCount = AtomicInteger()
        registry.registerShutdown("resource") { closeCount.incrementAndGet(); Unit }
        registry.shutdown()
        registry.shutdown()
        registry.close()
        assertEquals(1, closeCount.get())
    }
}

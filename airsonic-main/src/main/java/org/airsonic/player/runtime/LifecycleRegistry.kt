package org.airsonic.player.runtime

import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Framework-neutral registry for ordered startup callbacks and graceful shutdown resources. */
class LifecycleRegistry : AutoCloseable {
    data class LifecycleItem(
        private val itemName: String?,
        private val itemOrder: Int,
        private val itemCloseable: AutoCloseable?,
    ) {
        fun name(): String? = itemName
        fun order(): Int = itemOrder
        fun closeable(): AutoCloseable? = itemCloseable
    }

    data class StartupItem(
        private val itemName: String?,
        private val itemOrder: Int,
        private val itemAction: Runnable?,
    ) {
        fun name(): String? = itemName
        fun order(): Int = itemOrder
        fun action(): Runnable? = itemAction
    }

    private val startupItems = CopyOnWriteArrayList<StartupItem>()
    private val shutdownItems = CopyOnWriteArrayList<LifecycleItem>()
    private val shuttingDown = AtomicBoolean(false)
    private val shutdownComplete = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    fun registerStartup(name: String?, action: Runnable?) {
        registerStartup(name, 0, action)
    }

    fun registerStartup(name: String?, order: Int, action: Runnable?) {
        if (action == null) return
        startupItems.add(StartupItem(name, order, action))
        LOG.debug("Registered lifecycle startup action: {} (order {})", name, order)
    }

    fun registerShutdown(name: String?, closeable: AutoCloseable?) {
        registerShutdown(name, 0, closeable)
    }

    fun registerShutdown(name: String?, order: Int, closeable: AutoCloseable?) {
        if (closeable == null) return
        shutdownItems.add(LifecycleItem(name, order, closeable))
        LOG.debug("Registered lifecycle shutdown handler: {} (order {})", name, order)
    }

    fun isShuttingDown(): Boolean = shuttingDown.get()

    fun isShutdownComplete(): Boolean = shutdownComplete.get()

    fun getShutdownItems(): List<LifecycleItem> = Collections.unmodifiableList(shutdownItems)

    fun getStartupItems(): List<StartupItem> = Collections.unmodifiableList(startupItems)

    fun start() {
        if (shuttingDown.get() || !started.compareAndSet(false, true)) {
            throw IllegalStateException("Lifecycle has already started or is shutting down")
        }
        LOG.info("Running {} startup lifecycle items...", startupItems.size)
        val sorted = startupItems.sortedWith(Comparator.comparingInt(StartupItem::order))
        for (item in sorted) {
            try {
                LOG.info("Executing startup action: {}", item.name())
                item.action()!!.run()
            } catch (failure: RuntimeException) {
                handleStartupFailure(item, failure)
                throw failure
            } catch (failure: Error) {
                handleStartupFailure(item, failure)
                throw failure
            }
        }
    }

    fun shutdown() = shutdown(DEFAULT_SHUTDOWN_TIMEOUT)

    fun shutdown(totalTimeout: Duration) {
        if (!shuttingDown.compareAndSet(false, true)) {
            LOG.warn("LifecycleRegistry shutdown already in progress or completed")
            return
        }

        LOG.info("Initiating graceful shutdown for {} components (timeout {})...", shutdownItems.size, totalTimeout)
        val shutdownList = shutdownItems.asReversed().sortedByDescending(LifecycleItem::order)
        val deadline = System.currentTimeMillis() + totalTimeout.toMillis()

        for (item in shutdownList) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                LOG.warn("Shutdown timeout exceeded before shutting down: {}", item.name())
                break
            }
            try {
                LOG.info("Shutting down component: {}", item.name())
                item.closeable()!!.close()
            } catch (failure: Throwable) {
                LOG.error("Error shutting down component '{}'", item.name(), failure)
            }
        }

        shutdownItems.clear()
        shutdownComplete.set(true)
        LOG.info("LifecycleRegistry graceful shutdown complete.")
    }

    override fun close() = shutdown()

    private fun handleStartupFailure(item: StartupItem, failure: Throwable) {
        LOG.error("Error executing startup action '{}'", item.name(), failure)
        shutdown()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LifecycleRegistry::class.java)
        private val DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10)
    }
}

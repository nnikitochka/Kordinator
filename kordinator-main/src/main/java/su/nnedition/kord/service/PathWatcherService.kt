package su.nnedition.kord.service

import su.nnedition.kord.util.Util
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchEvent.Kind
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.BiConsumer

class PathWatcherService : AutoCloseable {
    private val watcherThread: ExecutorService =
        Executors.newSingleThreadExecutor(Util.getDaemonThreadfactory("path-watcher-thread-"))
    private val watcherFunctionThreadPool: ExecutorService =
        Executors.newCachedThreadPool(Util.getDaemonThreadfactory("path-watcher-function-thread-"))
    private val watchFunctions = ConcurrentHashMap<WatchKey, Map<Kind<*>, BiConsumer<Path, WatchEvent<Path>>>>()
    private val watchNames = ConcurrentHashMap<String, WatchKey>()
    private lateinit var watchService: WatchService

    private val watcherTask = Runnable {
        while (!Thread.currentThread().isInterrupted) {
            val key = try {
                watchService.take()
            } catch (_: ClosedWatchServiceException) {
                LOG.info("Watch service closed, exiting path watcher thread")
                break
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (failure: Exception) {
                LOG.warn("Watch service unable to poll. Directory monitoring may be off", failure)
                continue
            }

            val handlers = watchFunctions[key] ?: continue
            for (event in key.pollEvents()) {
                @Suppress("UNCHECKED_CAST")
                val typedEvent = event as WatchEvent<Path>
                handlers[event.kind()]?.let { handler ->
                    val watchedPath = key.watchable() as Path
                    watcherFunctionThreadPool.submit { handler.accept(watchedPath, typedEvent) }
                }
            }
            key.reset()
        }
    }

    init {
        watchService = FileSystems.getDefault().newWatchService()
        watcherThread.submit(watcherTask)
    }

    @Throws(IOException::class)
    fun setWatcher(
        watchable: Path,
        entryCreateConsumer: BiConsumer<Path, WatchEvent<Path>>,
        entryDeleteConsumer: BiConsumer<Path, WatchEvent<Path>>,
        entryModifyConsumer: BiConsumer<Path, WatchEvent<Path>>,
        overflowConsumer: BiConsumer<Path, WatchEvent<Path>>,
    ) = setWatcher(
        UUID.randomUUID().toString(), watchable,
        entryCreateConsumer, entryDeleteConsumer, entryModifyConsumer, overflowConsumer,
    )

    @Throws(IOException::class)
    fun setWatcher(
        id: String,
        watchable: Path,
        entryCreateConsumer: BiConsumer<Path, WatchEvent<Path>>,
        entryDeleteConsumer: BiConsumer<Path, WatchEvent<Path>>,
        entryModifyConsumer: BiConsumer<Path, WatchEvent<Path>>,
        overflowConsumer: BiConsumer<Path, WatchEvent<Path>>,
    ) {
        val functionMap = HashMap<Kind<*>, BiConsumer<Path, WatchEvent<Path>>>()
        functionMap[StandardWatchEventKinds.ENTRY_CREATE] = entryCreateConsumer
        functionMap[StandardWatchEventKinds.ENTRY_DELETE] = entryDeleteConsumer
        functionMap[StandardWatchEventKinds.ENTRY_MODIFY] = entryModifyConsumer
        functionMap[StandardWatchEventKinds.OVERFLOW] = overflowConsumer
        setWatcher(id, watchable, functionMap)
    }

    @Throws(IOException::class)
    fun setWatcher(
        id: String,
        watchable: Path,
        functionMap: Map<Kind<*>, BiConsumer<Path, WatchEvent<Path>>>,
    ) {
        val key = watchable.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.OVERFLOW,
        )
        watchFunctions[key] = functionMap
        watchNames.compute(id) { _, previousKey ->
            if (key != previousKey) invalidateWatcher(previousKey)
            key
        }
    }

    fun invalidateWatcher(id: String) = invalidateWatcher(watchNames.remove(id))

    fun invalidateWatcher(watchable: Path) {
        watchNames.entries.firstOrNull { it.value.watchable() == watchable }?.let { invalidateWatcher(it.key) }
    }

    fun invalidateWatcher(key: WatchKey?) {
        if (key != null) {
            key.cancel()
            watchFunctions.remove(key)
        }
    }

    /** A detached snapshot for diagnostics; reading it does not change active watchers. */
    val watchers: Map<String, String>
        get() = watchNames.mapValues { (_, key) -> key.watchable().toString() }

    override fun close() {
        LOG.info("Closing PathWatcherService...")
        watchNames.values.forEach { it.cancel() }
        watchNames.clear()
        watchFunctions.clear()
        try {
            if (::watchService.isInitialized) watchService.close()
        } catch (failure: IOException) {
            LOG.warn("Error closing watch service", failure)
        }
        watcherThread.shutdownNow()
        watcherFunctionThreadPool.shutdownNow()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PathWatcherService::class.java)
    }
}

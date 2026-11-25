package su.nnedition.kord.service

import su.nnedition.kord.util.Util
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Function

/** Framework-neutral scheduler for recurring and one-off background tasks. */
class TaskSchedulingService private constructor(
    private val executor: ScheduledExecutorService,
    private val clock: Clock,
    private val ownsExecutor: Boolean,
) : AutoCloseable {
    private val tasks = ConcurrentHashMap<String, ScheduledTask>()
    private val taskMetadata = ConcurrentHashMap<ScheduledTask, Map<String, Any>>()

    constructor() : this(createDefaultExecutor(DEFAULT_POOL_SIZE), Clock.systemUTC(), true)
    constructor(poolSize: Int) : this(createDefaultExecutor(poolSize), Clock.systemUTC(), true)
    constructor(executor: ScheduledExecutorService) : this(executor, Clock.systemUTC(), false)
    constructor(clock: Clock) : this(createDefaultExecutor(DEFAULT_POOL_SIZE), clock, true)
    constructor(executor: ScheduledExecutorService, clock: Clock) : this(executor, clock, false)

    fun scheduleTask(name: String, taskSupplier: Function<TaskSchedulingService, ScheduledTask>) =
        scheduleTask(name, taskSupplier, true)

    fun scheduleTask(
        name: String,
        taskSupplier: Function<TaskSchedulingService, ScheduledTask>,
        cancelIfExists: Boolean,
    ) {
        tasks.compute(name) { key, oldTask ->
            if (cancelIfExists && oldTask != null) {
                oldTask.cancel()
                taskMetadata.remove(oldTask)
            }
            val task = taskSupplier.apply(this)
            taskMetadata[task] = mapOf("name" to key, "created" to clock.instant())
            LOG.info("Task {} scheduled", key)
            task
        }
    }

    fun scheduleCronTask(runnable: Runnable, cronExpression: String): ScheduledTask =
        ScheduledTask(null, runnable, RunMetadata.Type.CRON, 0, 0, null)

    fun scheduleFixedDelayTask(
        name: String,
        task: Runnable,
        firstTime: Instant,
        period: Duration,
        cancelIfExists: Boolean,
    ) {
        val initialDelayMillis = maxOf(0, ChronoUnit.MILLIS.between(clock.instant(), firstTime))
        val delayMillis = period.toMillis()
        scheduleInternal(name, task, RunMetadata.Type.FIXED_DELAY, initialDelayMillis, delayMillis, cancelIfExists) {
            executor.scheduleWithFixedDelay(it, initialDelayMillis, delayMillis, TimeUnit.MILLISECONDS)
        }
    }

    fun scheduleAtFixedRate(
        name: String,
        task: Runnable,
        firstTime: Instant,
        period: Duration,
        cancelIfExists: Boolean,
    ) {
        val initialDelayMillis = maxOf(0, ChronoUnit.MILLIS.between(clock.instant(), firstTime))
        val periodMillis = period.toMillis()
        scheduleInternal(name, task, RunMetadata.Type.FIXED_RATE, initialDelayMillis, periodMillis, cancelIfExists) {
            executor.scheduleAtFixedRate(it, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS)
        }
    }

    fun scheduleOnce(name: String, task: Runnable, firstTime: Instant, cancelIfExists: Boolean) {
        val initialDelayMillis = maxOf(0, ChronoUnit.MILLIS.between(clock.instant(), firstTime))
        scheduleInternal(name, task, RunMetadata.Type.RUN_ONCE, initialDelayMillis, 0, cancelIfExists) {
            executor.schedule(it, initialDelayMillis, TimeUnit.MILLISECONDS)
        }
    }

    private inline fun scheduleInternal(
        name: String,
        task: Runnable,
        type: RunMetadata.Type,
        initialDelay: Long,
        interval: Long,
        cancelIfExists: Boolean,
        crossinline scheduler: (Runnable) -> ScheduledFuture<*>?,
    ) {
        tasks.compute(name) { key, oldTask ->
            if (cancelIfExists && oldTask != null) {
                oldTask.cancel()
                taskMetadata.remove(oldTask)
            }
            val safeRunnable = Runnable {
                try {
                    task.run()
                } catch (failure: Throwable) {
                    LOG.error("Uncaught error in scheduled task '{}'", name, failure)
                }
            }
            val future = if (!executor.isShutdown) scheduler(safeRunnable) else {
                LOG.warn("Cannot schedule task '{}': scheduler executor is already shut down", name)
                null
            }
            val scheduledTask = ScheduledTask(name, task, type, initialDelay, interval, future)
            taskMetadata[scheduledTask] = mapOf("name" to key, "created" to clock.instant())
            LOG.info("Task {} scheduled ({})", key, type)
            scheduledTask
        }
    }

    fun getScheduledTask(name: String): ScheduledTask? = tasks[name]

    fun unscheduleTask(name: String) {
        val task = tasks.remove(name)
        if (task != null) {
            task.cancel()
            taskMetadata.remove(task)
            LOG.info("Task {} unscheduled", name)
        }
    }

    fun getScheduledTasks(): Set<ScheduledTask> = HashSet(tasks.values)

    /** Read-only diagnostic snapshot; execution times are estimates derived from the schedule. */
    val taskInfos: List<TaskInfo>
        get() {
        val now = clock.instant()
        return tasks.entries.map { entry ->
            val task = entry.value
            val created = taskMetadata[task]?.get("created") as? Instant ?: now
            val scheduledBy = task.getRunnable()?.javaClass?.name ?: "unknown"
            TaskInfo(entry.key, created, scheduledBy, getRunMetadata(created, task, now))
        }
    }

    private fun getRunMetadata(created: Instant, task: ScheduledTask, now: Instant): RunMetadata? {
        if (task.getType() == RunMetadata.Type.RUN_ONCE) {
            val firstRun = created.plusMillis(task.getInitialDelay())
            return if (firstRun.isAfter(now)) {
                RunMetadata(firstRun, null, firstRun, RunMetadata.Type.RUN_ONCE)
            } else {
                RunMetadata(firstRun, firstRun, null, RunMetadata.Type.RUN_ONCE)
            }
        }
        if (task.getType() == RunMetadata.Type.FIXED_RATE || task.getType() == RunMetadata.Type.FIXED_DELAY) {
            val initialDelay = task.getInitialDelay()
            val interval = task.getInterval()
            val firstRun = created.plusMillis(initialDelay)
            val millis = ChronoUnit.MILLIS.between(firstRun, now)
            if (millis < 0) return RunMetadata(firstRun, null, firstRun, task.getType())
            if (interval <= 0) return RunMetadata(firstRun, firstRun, null, task.getType())
            val runs = millis / interval
            if (task.getType() == RunMetadata.Type.FIXED_RATE) {
                val lastRun = firstRun.plusMillis(interval * runs)
                return RunMetadata(firstRun, lastRun, lastRun.plusMillis(interval), RunMetadata.Type.FIXED_RATE)
            }
            return if (runs < 1) {
                RunMetadata(firstRun, firstRun, Instant.MIN, RunMetadata.Type.FIXED_DELAY)
            } else {
                RunMetadata(firstRun, Instant.MIN, Instant.MIN, RunMetadata.Type.FIXED_DELAY)
            }
        }
        return null
    }

    override fun close() = shutdown()

    fun shutdown() {
        LOG.info("Shutting down TaskSchedulingService...")
        tasks.values.forEach(ScheduledTask::cancel)
        tasks.clear()
        taskMetadata.clear()
        if (ownsExecutor) {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
            } catch (failure: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    class ScheduledTask(
        private val taskName: String?,
        private val taskRunnable: Runnable?,
        private val taskType: RunMetadata.Type,
        private val taskInitialDelay: Long,
        private val taskInterval: Long,
        private val taskFuture: ScheduledFuture<*>?,
    ) {
        fun getName(): String? = taskName
        fun getRunnable(): Runnable? = taskRunnable
        fun getType(): RunMetadata.Type = taskType
        fun getInitialDelay(): Long = taskInitialDelay
        fun getInterval(): Long = taskInterval
        fun getFuture(): ScheduledFuture<*>? = taskFuture

        /** Compatibility shim for tests inspecting task metadata. */
        fun getTask(): ScheduledTask = this

        fun cancel(): Boolean = taskFuture?.cancel(false) ?: true
    }

    data class TaskInfo(
        val name: String,
        val created: Instant,
        val scheduledBy: String,
        val runMetadata: RunMetadata?,
    ) {
        fun name(): String = name
        fun created(): Instant = created
        fun scheduledBy(): String = scheduledBy
        fun runMetadata(): RunMetadata? = runMetadata
    }

    class RunMetadata(
        val firstRun: Instant,
        val lastRun: Instant?,
        val nextRun: Instant?,
        val type: Type,
    ) {
        enum class Type { FIXED_RATE, FIXED_DELAY, RUN_ONCE, CRON }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TaskSchedulingService::class.java)
        private const val DEFAULT_POOL_SIZE = 10

        private fun createDefaultExecutor(poolSize: Int): ScheduledExecutorService =
            ScheduledThreadPoolExecutor(
                maxOf(1, poolSize),
                Util.getDaemonThreadfactory("task-scheduler-pool-"),
            ).apply { removeOnCancelPolicy = true }
    }
}

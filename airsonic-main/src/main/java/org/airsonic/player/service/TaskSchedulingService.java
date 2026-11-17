package org.airsonic.player.service;

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Framework-neutral scheduler for recurring and one-off background tasks.
 * Manages task lifecycles, execution estimates, and clean shutdown.
 */
public class TaskSchedulingService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TaskSchedulingService.class);
    private static final int DEFAULT_POOL_SIZE = 10;

    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final boolean ownsExecutor;

    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<ScheduledTask, Map<String, Object>> taskMetadata = new ConcurrentHashMap<>();

    public TaskSchedulingService() {
        this(createDefaultExecutor(DEFAULT_POOL_SIZE), Clock.systemUTC(), true);
    }

    public TaskSchedulingService(int poolSize) {
        this(createDefaultExecutor(poolSize), Clock.systemUTC(), true);
    }

    public TaskSchedulingService(ScheduledExecutorService executor) {
        this(executor, Clock.systemUTC(), false);
    }

    public TaskSchedulingService(Clock clock) {
        this(createDefaultExecutor(DEFAULT_POOL_SIZE), clock, true);
    }

    public TaskSchedulingService(ScheduledExecutorService executor, Clock clock) {
        this(executor, clock, false);
    }

    private TaskSchedulingService(ScheduledExecutorService executor, Clock clock, boolean ownsExecutor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ownsExecutor = ownsExecutor;
    }

    private static ScheduledExecutorService createDefaultExecutor(int poolSize) {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(
                Math.max(1, poolSize),
                Util.getDaemonThreadfactory("task-scheduler-pool-")
        );
        pool.setRemoveOnCancelPolicy(true);
        return pool;
    }

    public void scheduleTask(String name, Function<TaskSchedulingService, ScheduledTask> taskSupplier) {
        scheduleTask(name, taskSupplier, true);
    }

    public void scheduleTask(String name, Function<TaskSchedulingService, ScheduledTask> taskSupplier, boolean cancelIfExists) {
        tasks.compute(name, (k, oldTask) -> {
            if (cancelIfExists && oldTask != null) {
                oldTask.cancel();
                taskMetadata.remove(oldTask);
            }
            ScheduledTask task = taskSupplier.apply(this);
            taskMetadata.put(task, ImmutableMap.of("name", k, "created", clock.instant()));
            LOG.info("Task {} scheduled", k);
            return task;
        });
    }

    public ScheduledTask scheduleCronTask(Runnable runnable, String cronExpression) {
        return new ScheduledTask(null, runnable, RunMetadata.Type.CRON, 0, 0, null);
    }

    public void scheduleFixedDelayTask(String name, Runnable task, Instant firstTime, Duration period, boolean cancelIfExists) {
        long initialDelayMillis = Math.max(0, ChronoUnit.MILLIS.between(clock.instant(), firstTime));
        long delayMillis = period.toMillis();
        scheduleInternal(name, task, RunMetadata.Type.FIXED_DELAY, initialDelayMillis, delayMillis, cancelIfExists, (runnable) ->
                executor.scheduleWithFixedDelay(runnable, initialDelayMillis, delayMillis, TimeUnit.MILLISECONDS)
        );
    }

    public void scheduleAtFixedRate(String name, Runnable task, Instant firstTime, Duration period, boolean cancelIfExists) {
        long initialDelayMillis = Math.max(0, ChronoUnit.MILLIS.between(clock.instant(), firstTime));
        long periodMillis = period.toMillis();
        scheduleInternal(name, task, RunMetadata.Type.FIXED_RATE, initialDelayMillis, periodMillis, cancelIfExists, (runnable) ->
                executor.scheduleAtFixedRate(runnable, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS)
        );
    }

    public void scheduleOnce(String name, Runnable task, Instant firstTime, boolean cancelIfExists) {
        long initialDelayMillis = Math.max(0, ChronoUnit.MILLIS.between(clock.instant(), firstTime));
        scheduleInternal(name, task, RunMetadata.Type.RUN_ONCE, initialDelayMillis, 0, cancelIfExists, (runnable) ->
                executor.schedule(runnable, initialDelayMillis, TimeUnit.MILLISECONDS)
        );
    }

    private void scheduleInternal(String name, Runnable task, RunMetadata.Type type,
                                  long initialDelay, long interval, boolean cancelIfExists,
                                  Function<Runnable, ScheduledFuture<?>> scheduler) {
        tasks.compute(name, (k, oldTask) -> {
            if (cancelIfExists && oldTask != null) {
                oldTask.cancel();
                taskMetadata.remove(oldTask);
            }
            Runnable safeRunnable = () -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    LOG.error("Uncaught error in scheduled task '{}'", name, t);
                }
            };
            ScheduledFuture<?> future = null;
            if (!executor.isShutdown()) {
                future = scheduler.apply(safeRunnable);
            } else {
                LOG.warn("Cannot schedule task '{}': scheduler executor is already shut down", name);
            }
            ScheduledTask scheduledTask = new ScheduledTask(name, task, type, initialDelay, interval, future);
            taskMetadata.put(scheduledTask, ImmutableMap.of("name", k, "created", clock.instant()));
            LOG.info("Task {} scheduled ({})", k, type);
            return scheduledTask;
        });
    }

    public ScheduledTask getScheduledTask(String name) {
        return tasks.get(name);
    }

    public void unscheduleTask(String name) {
        ScheduledTask task = tasks.remove(name);
        if (task != null) {
            task.cancel();
            taskMetadata.remove(task);
            LOG.info("Task {} unscheduled", name);
        }
    }

    public Set<ScheduledTask> getScheduledTasks() {
        return new HashSet<>(tasks.values());
    }

    /** Read-only diagnostic snapshot; execution times are estimates derived from the schedule. */
    public List<TaskInfo> getTaskInfos() {
        Instant now = clock.instant();
        return tasks.entrySet().stream().map(entry -> {
            ScheduledTask task = entry.getValue();
            Map<String, Object> meta = taskMetadata.get(task);
            Instant created = meta != null ? (Instant) meta.get("created") : now;
            String scheduledBy = task.getRunnable() != null ? task.getRunnable().getClass().getName() : "unknown";
            return new TaskInfo(entry.getKey(), created, scheduledBy, getRunMetadata(created, task, now));
        }).toList();
    }

    public record TaskInfo(String name, Instant created, String scheduledBy, RunMetadata runMetadata) { }

    private RunMetadata getRunMetadata(Instant created, ScheduledTask scheduledTask, Instant now) {
        if (scheduledTask.getType() == RunMetadata.Type.RUN_ONCE) {
            Instant firstRun = created.plusMillis(scheduledTask.getInitialDelay());
            if (firstRun.isAfter(now)) {
                return new RunMetadata(firstRun, null, firstRun, RunMetadata.Type.RUN_ONCE);
            } else {
                return new RunMetadata(firstRun, firstRun, null, RunMetadata.Type.RUN_ONCE);
            }
        } else if (scheduledTask.getType() == RunMetadata.Type.FIXED_RATE || scheduledTask.getType() == RunMetadata.Type.FIXED_DELAY) {
            long initialDelay = scheduledTask.getInitialDelay();
            long interval = scheduledTask.getInterval();
            Instant firstRun = created.plusMillis(initialDelay);
            long millis = ChronoUnit.MILLIS.between(firstRun, now);
            if (millis < 0) {
                // firstRun will happen in the future
                return new RunMetadata(firstRun, null, firstRun, scheduledTask.getType());
            } else {
                // firstRun happened in the past
                if (interval <= 0) {
                    return new RunMetadata(firstRun, firstRun, null, scheduledTask.getType());
                }
                long runs = millis / interval;
                if (scheduledTask.getType() == RunMetadata.Type.FIXED_RATE) {
                    Instant lastRun = firstRun.plusMillis(interval * runs);
                    Instant nextRun = lastRun.plusMillis(interval);
                    return new RunMetadata(firstRun, lastRun, nextRun, RunMetadata.Type.FIXED_RATE);
                } else if (scheduledTask.getType() == RunMetadata.Type.FIXED_DELAY) {
                    if (runs < 1) {
                        return new RunMetadata(firstRun, firstRun, Instant.MIN, RunMetadata.Type.FIXED_DELAY);
                    } else {
                        return new RunMetadata(firstRun, Instant.MIN, Instant.MIN, RunMetadata.Type.FIXED_DELAY);
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        LOG.info("Shutting down TaskSchedulingService...");
        for (ScheduledTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        taskMetadata.clear();
        if (ownsExecutor && executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class ScheduledTask {
        private final String name;
        private final Runnable runnable;
        private final RunMetadata.Type type;
        private final long initialDelay;
        private final long interval;
        private final ScheduledFuture<?> future;

        public ScheduledTask(String name, Runnable runnable, RunMetadata.Type type,
                             long initialDelay, long interval, ScheduledFuture<?> future) {
            this.name = name;
            this.runnable = runnable;
            this.type = type;
            this.initialDelay = initialDelay;
            this.interval = interval;
            this.future = future;
        }

        public String getName() {
            return name;
        }

        public Runnable getRunnable() {
            return runnable;
        }

        public RunMetadata.Type getType() {
            return type;
        }

        public long getInitialDelay() {
            return initialDelay;
        }

        public long getInterval() {
            return interval;
        }

        public ScheduledFuture<?> getFuture() {
            return future;
        }

        /** Compatibility shim for tests inspecting task metadata. */
        public ScheduledTask getTask() {
            return this;
        }

        public boolean cancel() {
            if (future != null) {
                return future.cancel(false);
            }
            return true;
        }
    }

    public static class RunMetadata {
        private final Instant firstRun;
        private final Instant lastRun;
        private final Instant nextRun;
        private final Type type;

        public RunMetadata(Instant firstRun, Instant lastRun, Instant nextRun, Type type) {
            super();
            this.firstRun = firstRun;
            this.lastRun = lastRun;
            this.nextRun = nextRun;
            this.type = type;
        }

        public Instant getFirstRun() {
            return firstRun;
        }

        public Instant getLastRun() {
            return lastRun;
        }

        public Instant getNextRun() {
            return nextRun;
        }

        public Type getType() {
            return type;
        }

        public enum Type {
            FIXED_RATE, FIXED_DELAY, RUN_ONCE, CRON
        }
    }
}

package su.nnedition.kord.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulingServiceTest {

    private TaskSchedulingService service;

    @BeforeEach
    void setUp() {
        service = new TaskSchedulingService(4);
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void testScheduleOnceExecutesTask() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();

        service.scheduleOnce("once-task", () -> {
            count.incrementAndGet();
            latch.countDown();
        }, Instant.now().plusMillis(20), true);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Task should execute within 2 seconds");
        assertEquals(1, count.get());

        // Wait a bit more to ensure it only ran once
        Thread.sleep(50);
        assertEquals(1, count.get());
    }

    @Test
    void testScheduleOnceCancelledBeforeExecutionDoesNotRun() throws Exception {
        AtomicInteger count = new AtomicInteger();

        service.scheduleOnce("cancel-task", count::incrementAndGet, Instant.now().plusMillis(300), true);
        service.unscheduleTask("cancel-task");

        Thread.sleep(400);
        assertEquals(0, count.get(), "Cancelled task should not execute");
    }

    @Test
    void testScheduleAtFixedRateExecutesPeriodically() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger();

        service.scheduleAtFixedRate("rate-task", () -> {
            count.incrementAndGet();
            latch.countDown();
        }, Instant.now().plusMillis(10), Duration.ofMillis(30), true);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Task should execute at least 3 times");
        assertTrue(count.get() >= 3);

        service.unscheduleTask("rate-task");
        int snapshot = count.get();
        Thread.sleep(100);
        assertEquals(snapshot, count.get(), "Unscheduled task should stop running");
    }

    @Test
    void testScheduleFixedDelayExecutesWithDelay() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger();

        service.scheduleFixedDelayTask("delay-task", () -> {
            count.incrementAndGet();
            latch.countDown();
        }, Instant.now().plusMillis(10), Duration.ofMillis(30), true);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Fixed delay task should execute at least 3 times");
        assertTrue(count.get() >= 3);

        service.unscheduleTask("delay-task");
        int snapshot = count.get();
        Thread.sleep(100);
        assertEquals(snapshot, count.get(), "Unscheduled task should stop running");
    }

    @Test
    void testUncaughtExceptionInTaskDoesNotHaltFutureRuns() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();

        service.scheduleAtFixedRate("failing-task", () -> {
            int attempt = attempts.incrementAndGet();
            latch.countDown();
            if (attempt <= 2) {
                throw new RuntimeException("Deliberate test failure for attempt " + attempt);
            }
            successes.incrementAndGet();
        }, Instant.now().plusMillis(10), Duration.ofMillis(25), true);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Task should continue running despite exceptions");
        assertTrue(attempts.get() >= 3, "Attempts should reach at least 3");
        assertTrue(successes.get() >= 1, "Should succeed after failures");
    }

    @Test
    void testCancelIfExistsPolicy() {
        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger secondCount = new AtomicInteger();

        service.scheduleOnce("task-name", firstCount::incrementAndGet, Instant.now().plusSeconds(10), true);
        TaskSchedulingService.ScheduledTask first = service.getScheduledTask("task-name");
        assertNotNull(first);

        service.scheduleOnce("task-name", secondCount::incrementAndGet, Instant.now().plusSeconds(10), true);
        TaskSchedulingService.ScheduledTask second = service.getScheduledTask("task-name");
        assertNotNull(second);
        assertNotSame(first, second);
        assertTrue(first.getFuture().isCancelled(), "Previous task future should be cancelled");
    }

    @Test
    void testShutdownStopsAllTasksAndAwaitsTermination() throws Exception {
        AtomicInteger count = new AtomicInteger();

        service.scheduleAtFixedRate("to-shutdown", count::incrementAndGet,
                Instant.now().plusMillis(10), Duration.ofMillis(20), true);

        Thread.sleep(50);
        assertTrue(count.get() > 0);

        service.shutdown();
        int snapshot = count.get();
        Thread.sleep(80);
        assertEquals(snapshot, count.get(), "No tasks should execute after shutdown");
        assertTrue(service.getScheduledTasks().isEmpty());
    }
}

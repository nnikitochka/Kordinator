package su.nnedition.kord.service;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScheduledTaskSnapshotTest {
    @Test public void snapshotsPreserveScheduleEstimatesWithoutExecutingOrChangingTasks() throws Exception {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        TaskSchedulingService service = new TaskSchedulingService(clock);
        try {
            AtomicInteger executed = new AtomicInteger();
            Instant future = now.plusSeconds(3600);
            Duration period = Duration.ofMinutes(1);
            service.scheduleOnce("once", executed::incrementAndGet, future, true);
            service.scheduleAtFixedRate("rate", executed::incrementAndGet, future, period, true);
            service.scheduleFixedDelayTask("delay", executed::incrementAndGet, future, period, true);
            service.scheduleTask("cron", r -> r.scheduleCronTask(executed::incrementAndGet, "0 0 0 * * *"));
            assertEquals(4, service.getTaskInfos().size());
            for (String name : new String[]{"once", "rate", "delay"}) {
                var run = info(service, name).runMetadata();
                assertTrue(run.getFirstRun().isAfter(now));
                assertNull(run.getLastRun());
                assertEquals(run.getFirstRun(), run.getNextRun());
            }
            assertEquals(TaskSchedulingService.RunMetadata.Type.RUN_ONCE, info(service, "once").runMetadata().getType());
            assertEquals(TaskSchedulingService.RunMetadata.Type.FIXED_RATE, info(service, "rate").runMetadata().getType());
            assertEquals(TaskSchedulingService.RunMetadata.Type.FIXED_DELAY, info(service, "delay").runMetadata().getType());
            assertNull(info(service, "cron").runMetadata());
            when(clock.instant()).thenReturn(future.plusSeconds(1));
            var once = info(service, "once").runMetadata();
            assertEquals(once.getFirstRun(), once.getLastRun());
            assertNull(once.getNextRun());
            var rate = info(service, "rate").runMetadata();
            assertEquals(period, Duration.between(rate.getLastRun(), rate.getNextRun()));
            assertEquals(future, rate.getLastRun());
            when(clock.instant()).thenReturn(future.plus(period.multipliedBy(2)));
            var oldDelay = info(service, "delay").runMetadata();
            assertEquals(Instant.MIN, oldDelay.getLastRun());
            assertEquals(Instant.MIN, oldDelay.getNextRun());
            when(clock.instant()).thenReturn(future.plusSeconds(1));
            var recentDelay = info(service, "delay").runMetadata();
            assertEquals(recentDelay.getFirstRun(), recentDelay.getLastRun());
            assertEquals(Instant.MIN, recentDelay.getNextRun());
            assertEquals(0, executed.get());
            service.unscheduleTask("once");
            assertFalse(service.getTaskInfos().stream().anyMatch(task -> task.name().equals("once")));
            assertEquals(3, service.getScheduledTasks().size());
        } finally {
            service.close();
        }
    }

    private TaskSchedulingService.TaskInfo info(TaskSchedulingService service, String name) {
        return service.getTaskInfos().stream().filter(task -> task.name().equals(name)).findFirst().orElseThrow();
    }
}

package org.airsonic.player.service;

import org.airsonic.player.service.TaskSchedulingService.ScheduledTask;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ScheduledTaskSnapshotTest {
    @Test public void snapshotsPreserveScheduleEstimatesWithoutExecutingOrChangingTasks() throws Exception {
        TaskSchedulingService service = new TaskSchedulingService();
        try {
            AtomicInteger executed = new AtomicInteger();
            Instant future = Instant.now().plusSeconds(3600);
            Duration period = Duration.ofMinutes(1);
            service.scheduleOnce("once", executed::incrementAndGet, future, true);
            service.scheduleAtFixedRate("rate", executed::incrementAndGet, future, period, true);
            service.scheduleFixedDelayTask("delay", executed::incrementAndGet, future, period, true);
            service.scheduleTask("cron", r -> r.scheduleCronTask(executed::incrementAndGet, "0 0 0 * * *"));
            assertEquals(4, service.getTaskInfos().size());
            for (String name : new String[]{"once", "rate", "delay"}) {
                var run = info(service, name).runMetadata();
                assertTrue(run.getFirstRun().isAfter(Instant.now()));
                assertNull(run.getLastRun());
                assertEquals(run.getFirstRun(), run.getNextRun());
            }
            assertEquals(TaskSchedulingService.RunMetadata.Type.RUN_ONCE, info(service, "once").runMetadata().getType());
            assertEquals(TaskSchedulingService.RunMetadata.Type.FIXED_RATE, info(service, "rate").runMetadata().getType());
            assertEquals(TaskSchedulingService.RunMetadata.Type.FIXED_DELAY, info(service, "delay").runMetadata().getType());
            assertNull(info(service, "cron").runMetadata());
            retime(service, "once", Instant.EPOCH);
            var once = info(service, "once").runMetadata();
            assertEquals(once.getFirstRun(), once.getLastRun());
            assertNull(once.getNextRun());
            retime(service, "rate", Instant.EPOCH);
            var rate = info(service, "rate").runMetadata();
            assertEquals(period, Duration.between(rate.getLastRun(), rate.getNextRun()));
            assertFalse(rate.getLastRun().isAfter(Instant.now()));
            retime(service, "delay", Instant.EPOCH);
            var oldDelay = info(service, "delay").runMetadata();
            assertEquals(Instant.MIN, oldDelay.getLastRun());
            assertEquals(Instant.MIN, oldDelay.getNextRun());
            long initialDelay = service.getScheduledTask("delay").getInitialDelay();
            retime(service, "delay", Instant.now().minusMillis(initialDelay).minusSeconds(1));
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

    @SuppressWarnings("unchecked")
    private void retime(TaskSchedulingService service, String name, Instant created) {
        Map<ScheduledTask, Map<String, Object>> metadata = (Map<ScheduledTask, Map<String, Object>>) ReflectionTestUtils.getField(service, "taskMetadata");
        metadata.put(service.getScheduledTask(name), Map.of("name", name, "created", created));
    }
}

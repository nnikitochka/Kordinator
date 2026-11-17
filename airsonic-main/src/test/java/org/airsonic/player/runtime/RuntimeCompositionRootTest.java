package org.airsonic.player.runtime;

import org.junit.jupiter.api.Test;
import org.airsonic.player.spring.KtorConfiguration;
import org.airsonic.player.health.SimpleStatusAggregator;
import org.airsonic.player.health.Status;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeCompositionRootTest {

    @Test
    void suppliesTheExistingHealthStatusOrderByDefault() {
        RuntimeCompositionRoot runtime = new RuntimeCompositionRoot();

        assertEquals(List.of("DOWN", "OUT_OF_SERVICE", "UP", "UNKNOWN"),
                runtime.getConfiguration().getHealthStatusOrder());
    }

    @Test
    void keepsConfiguredOrderAndIgnoresWhitespaceAroundEntries() {
        RuntimeCompositionRoot runtime = new RuntimeCompositionRoot("FATAL, DOWN,UP");

        assertEquals(List.of("FATAL", "DOWN", "UP"), runtime.getConfiguration().getHealthStatusOrder());
    }

    @Test
    void doesNotExposeMutableConfiguration() {
        RuntimeConfiguration configuration = new RuntimeConfiguration("DOWN,UP");

        assertThrows(UnsupportedOperationException.class,
                () -> configuration.getHealthStatusOrder().add("UNKNOWN"));
    }

    @Test
    void springHealthAdapterUsesTheNeutralRuntimeOrder() {
        RuntimeCompositionRoot runtime = new RuntimeCompositionRoot("FATAL, DOWN, UP");
        SimpleStatusAggregator aggregator = (SimpleStatusAggregator)
                new KtorConfiguration().serverHealthStatusAggregator(runtime);

        assertEquals(new Status("FATAL"), aggregator.getAggregateStatus(Set.of(Status.DOWN, new Status("FATAL"))));
    }
}

package org.airsonic.player.runtime;

import org.junit.jupiter.api.Test;
import org.airsonic.player.spring.KtorConfiguration;
import org.airsonic.player.health.SimpleStatusAggregator;
import org.airsonic.player.health.Status;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    void runtimeStatusAggregatorUsesConfiguredOrder() {
        RuntimeCompositionRoot runtime = new RuntimeCompositionRoot("FATAL, DOWN, UP");
        SimpleStatusAggregator aggregator = (SimpleStatusAggregator)
                runtime.createStatusAggregator();

        assertEquals(new Status("FATAL"), aggregator.getAggregateStatus(Set.of(Status.DOWN, new Status("FATAL"))));
    }

    @Test
    void ownsCoverArtBackfillAndRegistersItsStartupActionOnce() {
        LifecycleRegistry lifecycle = new LifecycleRegistry();
        RuntimeCompositionRoot runtime = new RuntimeCompositionRoot(
                new RuntimeConfiguration("DOWN,UP"), lifecycle);
        org.airsonic.player.service.CoverArtService coverArtService = mock(org.airsonic.player.service.CoverArtService.class);

        org.airsonic.player.service.CoverArtAccentColorBackfill backfill =
                runtime.createCoverArtAccentColorBackfill(coverArtService);
        assertSame(backfill, runtime.createCoverArtAccentColorBackfill(coverArtService));
        assertEquals(1, lifecycle.getStartupItems().size());

        lifecycle.start();

        verify(coverArtService).backfillAccentColors();
    }
}

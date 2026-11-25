package su.nnedition.kord.runtime

import su.nnedition.kord.health.SimpleStatusAggregator
import su.nnedition.kord.health.Status
import su.nnedition.kord.service.CoverArtAccentColorBackfill
import su.nnedition.kord.service.CoverArtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class RuntimeCompositionRootTest {
    @Test
    fun suppliesTheExistingHealthStatusOrderByDefault() {
        val runtime = RuntimeCompositionRoot()
        assertEquals(listOf("DOWN", "OUT_OF_SERVICE", "UP", "UNKNOWN"), runtime.configuration.getHealthStatusOrder())
    }

    @Test
    fun keepsConfiguredOrderAndIgnoresWhitespaceAroundEntries() {
        val runtime = RuntimeCompositionRoot("FATAL, DOWN,UP")
        assertEquals(listOf("FATAL", "DOWN", "UP"), runtime.configuration.getHealthStatusOrder())
    }

    @Test
    fun doesNotExposeMutableConfiguration() {
        val configuration = RuntimeConfiguration("DOWN,UP")
        assertThrows(UnsupportedOperationException::class.java) {
            (configuration.getHealthStatusOrder() as MutableList<String>).add("UNKNOWN")
        }
    }

    @Test
    fun runtimeStatusAggregatorUsesConfiguredOrder() {
        val runtime = RuntimeCompositionRoot("FATAL, DOWN, UP")
        val aggregator = runtime.createStatusAggregator() as SimpleStatusAggregator
        assertEquals(Status("FATAL"), aggregator.getAggregateStatus(setOf(Status.DOWN, Status("FATAL"))))
    }

    @Test
    fun ownsCoverArtBackfillAndRegistersItsStartupActionOnce() {
        val lifecycle = LifecycleRegistry()
        val runtime = RuntimeCompositionRoot(RuntimeConfiguration("DOWN,UP"), lifecycle)
        val coverArtService = mock(CoverArtService::class.java)
        val backfill = runtime.createCoverArtAccentColorBackfill(coverArtService)

        assertSame(backfill, runtime.createCoverArtAccentColorBackfill(coverArtService))
        assertEquals(1, lifecycle.getStartupItems().size)
        lifecycle.start()
        verify(coverArtService).backfillAccentColors()
    }
}

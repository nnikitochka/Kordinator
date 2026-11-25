package su.nnedition.kord.runtime

import su.nnedition.kord.service.CoverArtService
import org.hsqldb.jdbc.JDBCDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RuntimeCompositionConcurrencyTest {
    @Test
    fun concurrentRequestsReuseComponentsAndRegisterLifecycleOnce() {
        val lifecycle = LifecycleRegistry()
        val root = RuntimeCompositionRoot(RuntimeConfiguration("DOWN,UP"), lifecycle)
        val coverArt = mock(CoverArtService::class.java)
        val workers = Executors.newFixedThreadPool(8)
        val barrier = CyclicBarrier(8)
        try {
            val requests = (1..8).map {
                workers.submit(Callable {
                    barrier.await(10, TimeUnit.SECONDS)
                    listOf(
                        root.createTaskSchedulingService(),
                        root.createCoverArtAccentColorBackfill(coverArt),
                        root.createLyricsService(),
                        root.createAnalyzerFactory(),
                    )
                })
            }
            val results = requests.map { it.get(10, TimeUnit.SECONDS) }
            for (result in results) {
                result.indices.forEach { assertSame(results.first()[it], result[it]) }
            }
            assertEquals(listOf("taskSchedulingService"), lifecycle.getShutdownItems().map { it.name() })
            assertEquals(listOf("coverArtAccentColorBackfill"), lifecycle.getStartupItems().map { it.name() })
            lifecycle.start()
            verify(coverArt).backfillAccentColors()
        } finally {
            workers.shutdownNow()
            lifecycle.shutdown()
        }
    }

    @Test
    fun rejectsASecondDataSourceAndKeepsTheOriginalGraph() {
        val lifecycle = LifecycleRegistry()
        val root = RuntimeCompositionRoot(RuntimeConfiguration("DOWN,UP"), lifecycle)
        val first = JDBCDataSource()
        val second = JDBCDataSource()
        try {
            val graph = root.createPersistenceGraph(first)
            val failure = assertThrows(IllegalStateException::class.java) { root.createPersistenceGraph(second) }
            assertEquals("Runtime composition root already owns another DataSource", failure.message)
            assertSame(first, root.createDataSource())
            assertSame(graph, root.createPersistenceGraph(first))
        } finally {
            lifecycle.shutdown()
        }
    }

    @Test
    fun rejectsANullDataSourceBeforeChangingGraphOwnership() {
        val root = RuntimeCompositionRoot()
        val failure = assertThrows(NullPointerException::class.java) { root.createPersistenceGraph(null) }
        assertEquals("dataSource", failure.message)
        assertEquals(emptyList<LifecycleRegistry.LifecycleItem>(), root.lifecycleRegistry.getShutdownItems())
    }
}

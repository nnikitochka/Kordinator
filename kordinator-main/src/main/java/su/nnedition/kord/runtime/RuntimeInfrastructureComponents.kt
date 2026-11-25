package su.nnedition.kord.runtime

import javax.sql.DataSource
import su.nnedition.kord.health.HealthContributorRegistry
import su.nnedition.kord.health.StatusAggregator
import su.nnedition.kord.monitor.MetricsManager
import su.nnedition.kord.service.PathWatcherService
import su.nnedition.kord.service.TaskSchedulingService

/** Creation boundary for the infrastructure components owned by one runtime. */
interface RuntimeInfrastructureComponents {
    fun createStatusAggregator(): StatusAggregator

    fun createMeterRegistry(): io.micrometer.core.instrument.MeterRegistry

    fun createDataSource(): DataSource

    fun createPersistenceGraph(): PersistenceGraph

    fun migrateDatabase(): Unit

    fun createMetricsManager(): MetricsManager

    fun createHealthContributorRegistry(
        dataSource: DataSource,
    ): HealthContributorRegistry

    fun createTaskSchedulingService(): TaskSchedulingService

    @Throws(Exception::class)
    fun createPathWatcherService(): PathWatcherService

    fun createPersistenceGraph(
        dataSource: DataSource?,
    ): PersistenceGraph

    fun createCacheManager(): javax.cache.CacheManager
}

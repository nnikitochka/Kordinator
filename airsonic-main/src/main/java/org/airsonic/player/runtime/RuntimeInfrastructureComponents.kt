package org.airsonic.player.runtime

import javax.sql.DataSource
import org.airsonic.player.health.HealthContributorRegistry
import org.airsonic.player.health.StatusAggregator
import org.airsonic.player.monitor.MetricsManager
import org.airsonic.player.service.PathWatcherService
import org.airsonic.player.service.TaskSchedulingService

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

package su.nnedition.kord.runtime

import javax.sql.DataSource
import su.nnedition.kord.health.HealthContributorRegistry
import su.nnedition.kord.health.StatusAggregator
import su.nnedition.kord.monitor.MetricsManager
import su.nnedition.kord.service.PathWatcherService
import su.nnedition.kord.service.TaskSchedulingService

/** Lazily assembles infrastructure components under the shared runtime lock. */
internal class RuntimeInfrastructureComposition(private val context: RuntimeCompositionContext) : RuntimeInfrastructureComponents {
    private var dataSource: DataSource? = null
    private var dataSourceShutdownRegistered: Boolean = false
    private var liquibaseMigrated: Boolean = false
    private var cacheManager: javax.cache.CacheManager? = null
    private var taskSchedulingService: TaskSchedulingService? = null
    private var pathWatcherService: PathWatcherService? = null
    private var healthContributorRegistry: HealthContributorRegistry? = null
    private var metricsManager: MetricsManager? = null
    private var meterRegistry: io.micrometer.core.instrument.MeterRegistry? = null
    private var statusAggregator: StatusAggregator? = null

    override fun createStatusAggregator(): StatusAggregator = synchronized(context.lock) {
        if (statusAggregator == null) {
            statusAggregator = RuntimeHealthFactory.createStatusAggregator(context.configuration)
        }
        return@synchronized statusAggregator!!
    }

    override fun createMeterRegistry(): io.micrometer.core.instrument.MeterRegistry = synchronized(context.lock) {
        if (meterRegistry == null) {
            meterRegistry = RuntimeHealthFactory.createMeterRegistry()
        }
        return@synchronized meterRegistry!!
    }

    override fun createDataSource(): DataSource = synchronized(context.lock) {
        if (dataSource == null) {
            ownDataSource(RuntimePersistenceFactory.createDataSource(context.configuration))
        }
        return@synchronized dataSource!!
    }

    private fun ownDataSource(
        candidate: DataSource,
    ): Unit = synchronized(context.lock) {
        java.util.Objects.requireNonNull(candidate, "dataSource")
        if (dataSource != null && dataSource !== candidate) {
            throw IllegalStateException("Runtime composition root already owns another DataSource")
        }
        dataSource = candidate
        if (candidate is AutoCloseable && !dataSourceShutdownRegistered) {
            context.lifecycleRegistry.registerShutdown("dataSource", candidate)
            dataSourceShutdownRegistered = true
        }
    }

    override fun createPersistenceGraph(): PersistenceGraph = synchronized(context.lock) {
        migrateDatabase()
        return@synchronized createPersistenceGraph(createDataSource())
    }

    override fun migrateDatabase(): Unit = synchronized(context.lock) {
        if (liquibaseMigrated) {
            return@synchronized Unit
        }
        RuntimePersistenceFactory.migrateDatabase(context.configuration, createDataSource())
        liquibaseMigrated = true
    }

    override fun createMetricsManager(): MetricsManager = synchronized(context.lock) {
        if (metricsManager == null) {
            metricsManager = RuntimeHealthFactory.createMetricsManager(context.configuration)
            context.lifecycleRegistry.registerShutdown("metricsManager", metricsManager)
        }
        return@synchronized metricsManager!!
    }

    override fun createHealthContributorRegistry(
        dataSource: DataSource,
    ): HealthContributorRegistry = synchronized(context.lock) {
        if (healthContributorRegistry == null) {
            healthContributorRegistry = RuntimeHealthFactory.createHealthContributorRegistry(dataSource)
        }
        return@synchronized healthContributorRegistry!!
    }

    override fun createTaskSchedulingService(): TaskSchedulingService = synchronized(context.lock) {
        if (taskSchedulingService == null) {
            taskSchedulingService = TaskSchedulingService()
            context.lifecycleRegistry.registerShutdown("taskSchedulingService", taskSchedulingService)
        }
        return@synchronized taskSchedulingService!!
    }

    @Throws(Exception::class)
    override fun createPathWatcherService(): PathWatcherService = synchronized(context.lock) {
        if (pathWatcherService == null) {
            pathWatcherService = PathWatcherService()
            context.lifecycleRegistry.registerShutdown("pathWatcherService", pathWatcherService)
        }
        return@synchronized pathWatcherService!!
    }

    override fun createPersistenceGraph(
        dataSource: DataSource?,
    ): PersistenceGraph = synchronized(context.lock) {
        val source = java.util.Objects.requireNonNull(dataSource, "dataSource")!!
        ownDataSource(source)
        if (context.persistenceGraph != null) {
            if (context.persistenceGraph!!.dataSource !== source) {
                throw IllegalStateException("Persistence graph already created for another DataSource")
            }
            return@synchronized context.persistenceGraph!!
        }

        context.persistenceGraph = RuntimePersistenceFactory.createPersistenceGraph(source)
        return@synchronized context.persistenceGraph!!
    }

    override fun createCacheManager(): javax.cache.CacheManager = synchronized(context.lock) {
        if (cacheManager == null) {
            cacheManager = RuntimePersistenceFactory.createCacheManager()
            context.lifecycleRegistry.registerShutdown("cacheManager", { cacheManager!!.close() })
        }
        return@synchronized cacheManager!!
    }
}

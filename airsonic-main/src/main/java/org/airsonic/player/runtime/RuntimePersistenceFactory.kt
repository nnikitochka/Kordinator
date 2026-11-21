package org.airsonic.player.runtime

import javax.cache.CacheManager
import javax.sql.DataSource

/** Builds persistence infrastructure used by the framework-neutral runtime composition root. */
object RuntimePersistenceFactory {
    @JvmStatic
    fun createDataSource(configuration: RuntimeConfiguration): DataSource =
        RuntimeDataSourceFactory.create(configuration)

    @JvmStatic
    fun migrateDatabase(configuration: RuntimeConfiguration, dataSource: DataSource) {
        RuntimeLiquibaseMigrator.update(configuration, dataSource)
    }

    @JvmStatic
    fun createPersistenceGraph(dataSource: DataSource): PersistenceGraph {
        val jdbcFactory = JdbcFactory()
        val transactionManager = jdbcFactory.transactionManager(dataSource)
        val jdbcHelper = jdbcFactory.jdbcHelper(dataSource, transactionManager)
        val daoGraph = DaoFactory().create(jdbcHelper, transactionManager)
        return PersistenceGraph(dataSource, transactionManager, jdbcHelper, daoGraph)
    }

    @JvmStatic
    fun createCacheManager(): CacheManager = CacheFactory().createCacheManager()
}

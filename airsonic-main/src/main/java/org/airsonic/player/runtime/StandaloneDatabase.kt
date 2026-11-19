package org.airsonic.player.runtime

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import java.nio.file.Files
import javax.sql.DataSource

/** Database resources owned by the standalone Ktor process. */
class StandaloneDatabase private constructor(
    val dataSource: HikariDataSource,
) : AutoCloseable {
    fun migrate() = migrate(dataSource)

    override fun close() = dataSource.close()

    companion object {
        private const val CHANGELOG = "liquibase/standalone/db.changelog-master.xml"
        fun connect(configuration: RuntimeConfiguration): StandaloneDatabase {
            val home = configuration.airsonicHome
            val defaultDatabase = home.resolve("db/youamp")
            Files.createDirectories(defaultDatabase.parent)

            val hikari = HikariConfig().apply {
                jdbcUrl = configuration.getProperty(
                    "airsonic.database.url",
                    "jdbc:hsqldb:file:$defaultDatabase;hsqldb.default_table_type=cached;hsqldb.cache_rows=100000;hsqldb.cache_size=65536;hsqldb.applog=0",
                )
                driverClassName = configuration.getProperty("airsonic.database.driver", "org.hsqldb.jdbc.JDBCDriver")
                username = configuration.getProperty("airsonic.database.username", "sa")
                password = configuration.getProperty("airsonic.database.password", "")
                maximumPoolSize = configuration.getIntProperty("airsonic.database.pool.max-size", 8)
                minimumIdle = configuration.getIntProperty("airsonic.database.pool.min-idle", 1)
                poolName = "airsonic-standalone-db"
            }
            return StandaloneDatabase(HikariDataSource(hikari))
        }

        fun migrate(dataSource: DataSource) {
            dataSource.connection.use { connection ->
                val database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(JdbcConnection(connection))
                try {
                    Liquibase(CHANGELOG, ClassLoaderResourceAccessor(), database)
                        .update(Contexts(), LabelExpression())
                } finally {
                    database.close()
                }
            }
        }
    }
}

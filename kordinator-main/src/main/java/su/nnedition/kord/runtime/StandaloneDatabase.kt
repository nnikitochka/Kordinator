package su.nnedition.kord.runtime

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
            val home = configuration.kordinatorHome
            val defaultDatabase = home.resolve("db/youamp")
            Files.createDirectories(defaultDatabase.parent)

            val hikari = HikariConfig().apply {
                jdbcUrl = configuration.getProperty(
                    "kordinator.database.url",
                    "jdbc:hsqldb:file:$defaultDatabase;hsqldb.default_table_type=cached;hsqldb.cache_rows=100000;hsqldb.cache_size=65536;hsqldb.applog=0",
                )
                driverClassName = configuration.getProperty("kordinator.database.driver", "org.hsqldb.jdbc.JDBCDriver")
                username = configuration.getProperty("kordinator.database.username", "sa")
                password = configuration.getProperty("kordinator.database.password", "")
                maximumPoolSize = configuration.getIntProperty("kordinator.database.pool.max-size", 8)
                minimumIdle = configuration.getIntProperty("kordinator.database.pool.min-idle", 1)
                poolName = "kordinator-standalone-db"
            }
            return StandaloneDatabase(HikariDataSource(hikari))
        }

        fun migrate(dataSource: DataSource) {
            System.setProperty("liquibase.analytics.enabled", "false")
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

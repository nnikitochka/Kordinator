package su.nnedition.kord.health

import org.slf4j.LoggerFactory
import javax.sql.DataSource

/** Health indicator verifying database connectivity and responsiveness. */
class DatabaseHealthIndicator(
    private val dataSource: DataSource,
    private val timeoutSeconds: Int,
) : HealthIndicator {
    constructor(dataSource: DataSource) : this(dataSource, DEFAULT_TIMEOUT_SECONDS)

    override fun health(): Health = try {
        dataSource.connection.use { connection ->
            if (!connection.isValid(timeoutSeconds)) {
                Health.down().withDetail("error", "Database connection validation failed").build()
            } else {
                Health.up()
                    .withDetail("database", connection.metaData.databaseProductName)
                    .withDetail("validationQuery", "isValid()")
                    .build()
            }
        }
    } catch (failure: Exception) {
        log.warn("Database health check failed", failure)
        Health.down(failure).build()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 5
        private val log = LoggerFactory.getLogger(DatabaseHealthIndicator::class.java)
    }
}

package su.nnedition.kord.dao

import su.nnedition.kord.util.LambdaUtils.ThrowingBiFunction
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.util.function.Consumer

/** Provides a short-lived connection to database export and import operations. */
class DatabaseDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    @Throws(Exception::class)
    fun exportDB(
        tempWorkingDir: Path,
        exportFunction: ThrowingBiFunction<Path, Connection, Boolean, Exception>
    ): Boolean = getJdbcHelper().getDataSource().connection.use { connection ->
        exportFunction.apply(tempWorkingDir, connection)
    }

    fun importDB(importFunction: Consumer<Connection>) {
        try {
            getJdbcHelper().getDataSource().connection.use(importFunction::accept)
        } catch (e: Exception) {
            LOG.info("DB Import failed!", e)
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(DatabaseDao::class.java)
    }
}

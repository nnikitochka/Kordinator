package su.nnedition.kord.runtime

import su.nnedition.kord.dao.JdbcHelper
import su.nnedition.kord.dao.TransactionManager
import javax.sql.DataSource

/** Creates the shared JDBC infrastructure without depending on the application framework. */
class JdbcFactory {
    fun transactionManager(dataSource: DataSource): TransactionManager = TransactionManager(dataSource)

    fun jdbcHelper(dataSource: DataSource, transactionManager: TransactionManager): JdbcHelper =
        JdbcHelper(dataSource, transactionManager)
}

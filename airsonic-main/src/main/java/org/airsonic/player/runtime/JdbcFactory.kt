package org.airsonic.player.runtime

import org.airsonic.player.dao.JdbcHelper
import org.airsonic.player.dao.TransactionManager
import javax.sql.DataSource

/** Creates the shared JDBC infrastructure without depending on the application framework. */
class JdbcFactory {
    fun transactionManager(dataSource: DataSource): TransactionManager = TransactionManager(dataSource)

    fun jdbcHelper(dataSource: DataSource, transactionManager: TransactionManager): JdbcHelper =
        JdbcHelper(dataSource, transactionManager)
}

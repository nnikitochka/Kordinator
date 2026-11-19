package org.airsonic.player.runtime

import org.airsonic.player.dao.JdbcHelper
import org.airsonic.player.dao.TransactionManager
import org.airsonic.player.dao.ExternalTransactionConnectionContext
import javax.sql.DataSource

/** Creates the shared JDBC infrastructure without depending on the application framework. */
class JdbcFactory {
    fun transactionManager(dataSource: DataSource): TransactionManager =
        transactionManager(dataSource, ExternalTransactionConnectionContext.NONE)

    fun transactionManager(
        dataSource: DataSource,
        externalTransactionContext: ExternalTransactionConnectionContext,
    ): TransactionManager = TransactionManager(dataSource, externalTransactionContext)

    fun jdbcHelper(dataSource: DataSource, transactionManager: TransactionManager): JdbcHelper =
        JdbcHelper(dataSource, transactionManager)
}

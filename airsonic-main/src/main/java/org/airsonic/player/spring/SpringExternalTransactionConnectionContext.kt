package org.airsonic.player.spring

import org.airsonic.player.dao.ExternalTransactionConnectionContext
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.interceptor.TransactionAspectSupport
import java.sql.Connection
import javax.sql.DataSource

/** Spring bridge kept at the application bootstrap boundary. */
class SpringExternalTransactionConnectionContext : ExternalTransactionConnectionContext {
    override fun currentConnection(dataSource: DataSource): Connection? =
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            DataSourceUtils.getConnection(dataSource)
        } else {
            null
        }

    override fun releaseConnection(connection: Connection, dataSource: DataSource): Boolean {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return false
        DataSourceUtils.releaseConnection(connection, dataSource)
        return true
    }

    override fun markRollbackOnly(dataSource: DataSource) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
    }
}

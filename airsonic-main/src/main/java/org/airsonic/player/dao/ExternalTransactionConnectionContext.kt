package org.airsonic.player.dao

import java.sql.Connection
import javax.sql.DataSource

/** Optional boundary for sharing connections managed by an enclosing framework transaction. */
interface ExternalTransactionConnectionContext {
    fun currentConnection(dataSource: DataSource): Connection?

    /** Returns true when the connection was released by the external transaction manager. */
    fun releaseConnection(connection: Connection, dataSource: DataSource): Boolean

    /** Marks the enclosing transaction for rollback when joined work fails. */
    fun markRollbackOnly(dataSource: DataSource)

    companion object {
        @JvmField
        val NONE: ExternalTransactionConnectionContext = object : ExternalTransactionConnectionContext {
            override fun currentConnection(dataSource: DataSource): Connection? = null
            override fun releaseConnection(connection: Connection, dataSource: DataSource): Boolean = false
            override fun markRollbackOnly(dataSource: DataSource) = Unit
        }
    }
}

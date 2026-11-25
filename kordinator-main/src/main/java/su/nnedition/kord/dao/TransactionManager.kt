package su.nnedition.kord.dao

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.Objects
import java.util.function.Consumer
import javax.sql.DataSource

/** Framework-neutral transaction manager supporting thread-bound connections and nested transactions. */
class TransactionManager(dataSource: DataSource?) {
    private val dataSource: DataSource = Objects.requireNonNull(dataSource, "dataSource")!!

    fun getDataSource(): DataSource = dataSource

    @Throws(SQLException::class)
    fun getConnection(): Connection {
        val state = CURRENT_TRANSACTION.get()
        if (state != null && !state.connection.isClosed) return state.connection
        return dataSource.connection
    }

    fun releaseConnection(connection: Connection?) {
        if (connection == null) return
        val state = CURRENT_TRANSACTION.get()
        if (state != null && connection === state.connection) return
        try {
            connection.close()
        } catch (failure: SQLException) {
            LOG.warn("Failed to close JDBC connection", failure)
        }
    }

    fun <T> execute(action: TransactionCallback<T>): T = execute(
        TransactionDefinition(PROPAGATION_REQUIRED, ISOLATION_DEFAULT), action
    )

    fun executeWithoutResult(action: Consumer<TransactionStatus>) {
        execute(TransactionDefinition(PROPAGATION_REQUIRED, ISOLATION_DEFAULT)) { status ->
            action.accept(status)
            null
        }
    }

    fun executeWithoutResult(definition: TransactionDefinition, action: Consumer<TransactionStatus>) {
        execute(definition) { status ->
            action.accept(status)
            null
        }
    }

    fun <T> execute(definition: TransactionDefinition, action: TransactionCallback<T>): T {
        val parent = CURRENT_TRANSACTION.get()
        if (parent != null && definition.propagation == PROPAGATION_REQUIRED) {
            val status = TransactionStatus(false)
            try {
                val result = action.doInTransaction(status)
                if (status.isRollbackOnly()) parent.rollbackOnly = true
                return result
            } catch (failure: Throwable) {
                parent.rollbackOnly = true
                throw propagate("Transaction action failed", failure)
            }
        }

        var connection: Connection? = null
        var originalAutoCommit = true
        var originalIsolation = ISOLATION_DEFAULT
        try {
            connection = dataSource.connection
            originalAutoCommit = connection.autoCommit
            if (originalAutoCommit) connection.autoCommit = false
            if (definition.isolationLevel != ISOLATION_DEFAULT) {
                originalIsolation = connection.transactionIsolation
                connection.transactionIsolation = definition.isolationLevel
            }

            val state = TransactionState(connection, parent)
            CURRENT_TRANSACTION.set(state)
            val status = TransactionStatus(true)
            val result = action.doInTransaction(status)

            if (status.isRollbackOnly() || state.rollbackOnly) connection.rollback() else connection.commit()
            return result
        } catch (failure: Throwable) {
            try {
                connection?.rollback()
            } catch (rollbackFailure: SQLException) {
                LOG.error("Failed to rollback transaction", rollbackFailure)
            }
            throw propagate("Transaction failed", failure)
        } finally {
            CURRENT_TRANSACTION.set(parent)
            connection?.let { conn ->
                try {
                    if (definition.isolationLevel != ISOLATION_DEFAULT && originalIsolation != ISOLATION_DEFAULT) {
                        conn.transactionIsolation = originalIsolation
                    }
                    if (originalAutoCommit != conn.autoCommit) conn.autoCommit = originalAutoCommit
                } catch (restoreFailure: SQLException) {
                    LOG.warn("Failed to restore connection state", restoreFailure)
                }
                try {
                    conn.close()
                } catch (closeFailure: SQLException) {
                    LOG.warn("Failed to close transaction connection", closeFailure)
                }
            }
        }
    }

    private class TransactionState(val connection: Connection, val parent: TransactionState?) {
        var rollbackOnly: Boolean = false
    }

    class TransactionDefinition(val propagation: Int, val isolationLevel: Int)

    class TransactionStatus(private val newTransaction: Boolean) {
        private var rollbackOnly = false

        fun isNewTransaction(): Boolean = newTransaction

        fun isRollbackOnly(): Boolean = rollbackOnly

        fun setRollbackOnly() {
            rollbackOnly = true
        }
    }

    fun interface TransactionCallback<T> {
        @Throws(Exception::class)
        fun doInTransaction(status: TransactionStatus): T
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TransactionManager::class.java)
        private val CURRENT_TRANSACTION = ThreadLocal<TransactionState>()

        const val ISOLATION_DEFAULT = -1
        const val ISOLATION_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED
        const val ISOLATION_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED
        const val ISOLATION_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ
        const val ISOLATION_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE
        const val PROPAGATION_REQUIRED = 0
        const val PROPAGATION_REQUIRES_NEW = 1

        private fun propagate(message: String, failure: Throwable): RuntimeException = when (failure) {
            is RuntimeException -> failure
            is Error -> throw failure
            else -> RuntimeException(message, failure)
        }
    }
}

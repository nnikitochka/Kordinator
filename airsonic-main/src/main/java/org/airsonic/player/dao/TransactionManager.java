package org.airsonic.player.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Framework-neutral transaction manager supporting thread-bound connections,
 * nested transactions, savepoints, rollback, and optional external transaction participation.
 */
public class TransactionManager {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionManager.class);

    public static final int ISOLATION_DEFAULT = -1;
    public static final int ISOLATION_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED;
    public static final int ISOLATION_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED;
    public static final int ISOLATION_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ;
    public static final int ISOLATION_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE;

    public static final int PROPAGATION_REQUIRED = 0;
    public static final int PROPAGATION_REQUIRES_NEW = 1;

    private static final ThreadLocal<TransactionState> CURRENT_TRANSACTION = new ThreadLocal<>();

    private final DataSource dataSource;
    private final ExternalTransactionConnectionContext externalTransactionContext;

    public TransactionManager(DataSource dataSource) {
        this(dataSource, ExternalTransactionConnectionContext.NONE);
    }

    public TransactionManager(DataSource dataSource,
            ExternalTransactionConnectionContext externalTransactionContext) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.externalTransactionContext = Objects.requireNonNull(externalTransactionContext,
                "externalTransactionContext");
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        TransactionState state = CURRENT_TRANSACTION.get();
        if (state != null && state.connection != null && !state.connection.isClosed()) {
            return state.connection;
        }

        Connection externalConnection = externalTransactionContext.currentConnection(dataSource);
        if (externalConnection != null) {
            return externalConnection;
        }

        return dataSource.getConnection();
    }

    public void releaseConnection(Connection conn) {
        if (conn == null) {
            return;
        }
        TransactionState state = CURRENT_TRANSACTION.get();
        if (state != null && conn == state.connection) {
            // Connection is managed by active transaction; do not close now
            return;
        }

        if (externalTransactionContext.releaseConnection(conn, dataSource)) {
            return;
        }

        try {
            conn.close();
        } catch (SQLException e) {
            LOG.warn("Failed to close JDBC connection", e);
        }
    }

    public <T> T execute(TransactionCallback<T> action) {
        return execute(new TransactionDefinition(PROPAGATION_REQUIRED, ISOLATION_DEFAULT), action);
    }

    public void executeWithoutResult(Consumer<TransactionStatus> action) {
        execute(new TransactionDefinition(PROPAGATION_REQUIRED, ISOLATION_DEFAULT), status -> {
            action.accept(status);
            return null;
        });
    }

    public void executeWithoutResult(TransactionDefinition def, Consumer<TransactionStatus> action) {
        execute(def, status -> {
            action.accept(status);
            return null;
        });
    }

    public <T> T execute(TransactionDefinition def, TransactionCallback<T> action) {
        TransactionState parent = CURRENT_TRANSACTION.get();

        if (parent != null && def.propagation == PROPAGATION_REQUIRED) {
            // Join existing transaction
            TransactionStatus status = new TransactionStatus(false);
            try {
                T result = action.doInTransaction(status);
                if (status.isRollbackOnly()) {
                    parent.rollbackOnly = true;
                }
                return result;
            } catch (Throwable t) {
                parent.rollbackOnly = true;
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                if (t instanceof Error) {
                    throw (Error) t;
                }
                throw new RuntimeException("Transaction action failed", t);
            }
        }

        // Start new transaction (either top-level or PROPAGATION_REQUIRES_NEW)
        Connection conn = null;
        boolean originalAutoCommit = true;
        int originalIsolation = ISOLATION_DEFAULT;
        TransactionState state = null;

        try {
            conn = dataSource.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }
            if (def.isolationLevel != ISOLATION_DEFAULT) {
                originalIsolation = conn.getTransactionIsolation();
                conn.setTransactionIsolation(def.isolationLevel);
            }

            state = new TransactionState(conn, parent);
            CURRENT_TRANSACTION.set(state);

            TransactionStatus status = new TransactionStatus(true);
            T result = action.doInTransaction(status);

            if (status.isRollbackOnly() || state.rollbackOnly) {
                conn.rollback();
            } else {
                conn.commit();
            }

            return result;
        } catch (Throwable t) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOG.error("Failed to rollback transaction", ex);
                }
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new RuntimeException("Transaction failed", t);
        } finally {
            CURRENT_TRANSACTION.set(parent);
            if (conn != null) {
                try {
                    if (def.isolationLevel != ISOLATION_DEFAULT && originalIsolation != ISOLATION_DEFAULT) {
                        conn.setTransactionIsolation(originalIsolation);
                    }
                    if (originalAutoCommit != conn.getAutoCommit()) {
                        conn.setAutoCommit(originalAutoCommit);
                    }
                } catch (SQLException ex) {
                    LOG.warn("Failed to restore connection state", ex);
                }
                try {
                    conn.close();
                } catch (SQLException ex) {
                    LOG.warn("Failed to close transaction connection", ex);
                }
            }
        }
    }

    public static final class TransactionDefinition {
        private final int propagation;
        private final int isolationLevel;

        public TransactionDefinition(int propagation, int isolationLevel) {
            this.propagation = propagation;
            this.isolationLevel = isolationLevel;
        }

        public int getPropagation() {
            return propagation;
        }

        public int getIsolationLevel() {
            return isolationLevel;
        }
    }

    public static final class TransactionStatus {
        private final boolean isNewTransaction;
        private boolean rollbackOnly;

        public TransactionStatus(boolean isNewTransaction) {
            this.isNewTransaction = isNewTransaction;
        }

        public boolean isNewTransaction() {
            return isNewTransaction;
        }

        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        public void setRollbackOnly() {
            this.rollbackOnly = true;
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T doInTransaction(TransactionStatus status) throws Exception;
    }

    private static final class TransactionState {
        final Connection connection;
        final TransactionState parent;
        boolean rollbackOnly;

        TransactionState(Connection connection, TransactionState parent) {
            this.connection = connection;
            this.parent = parent;
        }
    }
}

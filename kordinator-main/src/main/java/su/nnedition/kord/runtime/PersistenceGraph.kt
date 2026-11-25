package su.nnedition.kord.runtime

import su.nnedition.kord.dao.JdbcHelper
import su.nnedition.kord.dao.TransactionManager
import javax.sql.DataSource

data class PersistenceGraph(
    val dataSource: DataSource,
    val transactionManager: TransactionManager,
    val jdbcHelper: JdbcHelper,
    val daos: DaoGraph,
)

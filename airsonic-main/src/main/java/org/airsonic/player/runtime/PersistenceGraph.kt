package org.airsonic.player.runtime

import org.airsonic.player.dao.JdbcHelper
import org.airsonic.player.dao.TransactionManager
import javax.sql.DataSource

data class PersistenceGraph(
    val dataSource: DataSource,
    val transactionManager: TransactionManager,
    val jdbcHelper: JdbcHelper,
    val daos: DaoGraph,
)

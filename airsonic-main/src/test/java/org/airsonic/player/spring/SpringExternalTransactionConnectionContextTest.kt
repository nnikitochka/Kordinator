package org.airsonic.player.spring

import org.airsonic.player.dao.TransactionManager
import org.hsqldb.jdbc.JDBCDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class SpringExternalTransactionConnectionContextTest {
    @Test
    fun `jdbc helper connection participates in enclosing spring transaction`() {
        val dataSource = JDBCDataSource().apply {
            setUrl("jdbc:hsqldb:mem:spring_tx_${UUID.randomUUID().toString().replace('-', '_')}")
            setUser("sa")
            setPassword("")
        }
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE tx_probe (id INTEGER PRIMARY KEY)") }
        }

        val transactionManager = TransactionManager(dataSource, SpringExternalTransactionConnectionContext())
        val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource))

        transactionTemplate.execute {
            transactionManager.execute { status ->
                val connection = transactionManager.getConnection()
                connection.createStatement().use { it.executeUpdate("INSERT INTO tx_probe (id) VALUES (1)") }
                transactionManager.releaseConnection(connection)

                assertFalse(connection.isClosed, "Spring still owns the connection until transaction completion")
                null
            }
            null
        }

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM tx_probe").use { resultSet ->
                    resultSet.next()
                    assertEquals(1, resultSet.getInt(1))
                }
            }
        }
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("DROP TABLE tx_probe") }
        }
    }
}

package org.airsonic.player.runtime

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StandaloneDatabaseTest {
    @Test
    fun migratesLocalUsersAndSessionsWithoutSpring() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_auth_schema"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 1
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            StandaloneDatabase.migrate(dataSource)

            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT COUNT(*) FROM local_user").use { statement ->
                    statement.executeQuery().use { rows ->
                        assertNotNull(rows)
                        rows.next()
                        assertEquals(0, rows.getInt(1))
                    }
                }
                connection.prepareStatement("SELECT COUNT(*) FROM user_session").use { statement ->
                    statement.executeQuery().use { rows ->
                        assertNotNull(rows)
                        rows.next()
                        assertEquals(0, rows.getInt(1))
                    }
                }
            }
        }
    }
}

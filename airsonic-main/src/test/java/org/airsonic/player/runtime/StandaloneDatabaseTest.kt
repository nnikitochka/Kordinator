package org.airsonic.player.runtime

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.airsonic.player.security.LocalAuthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun appliesLibraryAclMigrationToExistingUsersAndSessions() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_acl_upgrade"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            val userId = auth.createInitialAdmin("existing-admin", "a strong password for tests")
            val token = auth.login("existing-admin", "a strong password for tests")!!.token

            // Recreate the latest schema state before the ACL changeSet while retaining user/session data.
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute("DROP TABLE local_user_library") }
                connection.prepareStatement(
                    "DELETE FROM databasechangelog WHERE id = ? AND author = ?",
                ).use { statement ->
                    statement.setString(1, "standalone-user-library-access-1")
                    statement.setString(2, "kordinator")
                    assertEquals(1, statement.executeUpdate())
                }
            }

            StandaloneDatabase.migrate(dataSource)

            assertEquals("existing-admin", auth.authenticate(token)?.username)
            assertTrue(auth.setAllowedLibraries(userId, setOf(17L, 23L)))
            assertEquals(setOf(17L, 23L), auth.authenticate(token)?.allowedLibraryIds)
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT COUNT(*) FROM user_session").use { statement ->
                    statement.executeQuery().use { rows -> rows.next(); assertEquals(1, rows.getInt(1)) }
                }
            }
        }
    }
}

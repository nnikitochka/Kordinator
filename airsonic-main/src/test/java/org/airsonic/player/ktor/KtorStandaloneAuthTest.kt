package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LastEnabledAdministratorException
import org.airsonic.player.security.LocalAuthService
import su.nnedition.kord.api.configureApiRouting
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KtorStandaloneAuthTest {
    @Test
    fun deletingUserRemovesOwnedDataAndRevokesSessions() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_user_delete"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            val adminId = auth.createInitialAdmin("admin", "a strong password for tests")
            val victimId = auth.createUser("listener", "listener password for tests")
            val otherId = auth.createUser("another", "another strong password for tests")
            val victimSession = auth.login("listener", "listener password for tests")!!
            val otherSession = auth.login("another", "another strong password for tests")!!
            val adminToken = auth.login("admin", "a strong password for tests")!!.token

            val playlistId = dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO user_playlist (owner_id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    arrayOf("id"),
                ).use { statement ->
                    statement.setLong(1, victimId)
                    statement.setString(2, "Victim playlist")
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
                }
            }
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO user_playlist_track (playlist_id, position, track_id) VALUES (?, 0, 12345)",
                ).use { statement ->
                    statement.setLong(1, playlistId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO favorite_track (user_id, track_id, created_at) VALUES (?, 12345, CURRENT_TIMESTAMP)",
                ).use { statement ->
                    statement.setLong(1, victimId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO favorite_album (user_id, library_id, album_name, created_at) VALUES (?, 77, 'Album', CURRENT_TIMESTAMP)",
                ).use { statement ->
                    statement.setLong(1, victimId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO favorite_artist (user_id, library_id, artist_name, created_at) VALUES (?, 77, 'Artist', CURRENT_TIMESTAMP)",
                ).use { statement ->
                    statement.setLong(1, victimId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("INSERT INTO local_user_library (user_id, library_id) VALUES (?, 77)").use { statement ->
                    statement.setLong(1, victimId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO user_play_queue (user_id, current_track_id, position_ms, updated_at) VALUES (?, 12345, 1000, CURRENT_TIMESTAMP)",
                ).use { statement ->
                    statement.setLong(1, victimId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("INSERT INTO user_play_queue_track (user_id, position, track_id) VALUES (?, 0, 12345)").use { statement ->
                    statement.setLong(1, victimId)
                    statement.executeUpdate()
                }
            }

            testApplication {
                application { configureApiRouting(auth) }

                assertEquals(HttpStatusCode.Forbidden, client.delete("/api/v1/admin/users/$victimId") {
                    bearerAuth(victimSession.token)
                }.status)
                assertEquals(HttpStatusCode.Conflict, client.delete("/api/v1/admin/users/$adminId") {
                    bearerAuth(adminToken)
                }.status)
                assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/admin/users/999999") {
                    bearerAuth(adminToken)
                }.status)

                val deleted = client.delete("/api/v1/admin/users/$victimId") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.NoContent, deleted.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(victimSession.token) }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"listener","password":"listener password for tests"}""")
                }.status)
                assertEquals(HttpStatusCode.OK, client.get("/api/v1/me") { bearerAuth(otherSession.token) }.status)

                dataSource.connection.use { connection ->
                    fun count(sql: String, id: Long): Long = connection.prepareStatement(sql).use { statement ->
                        statement.setLong(1, id)
                        statement.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
                    }
                    assertEquals(0L, count("SELECT COUNT(*) FROM local_user WHERE id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM user_session WHERE user_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM user_playlist WHERE owner_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM user_playlist_track WHERE playlist_id = ?", playlistId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM favorite_track WHERE user_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM favorite_album WHERE user_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM favorite_artist WHERE user_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM local_user_library WHERE user_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM user_play_queue WHERE user_id = ?", victimId))
                    assertEquals(0L, count("SELECT COUNT(*) FROM user_play_queue_track WHERE user_id = ?", victimId))
                    assertEquals(1L, count("SELECT COUNT(*) FROM local_user WHERE id = ?", otherId))
                }
            }

            assertFailsWith<LastEnabledAdministratorException> { auth.deleteUser(adminId) }
            assertEquals("ADMIN", auth.users().single { it.id == adminId }.role)
        }
    }

    @Test
    fun administratorsCanRenameUsersWithoutRevokingTheirSessions() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_user_rename"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val userId = auth.createUser("listener", "listener password for tests")
            val userSession = auth.login("listener", "listener password for tests")!!
            val adminToken = auth.login("admin", "a strong password for tests")!!.token

            testApplication {
                application { configureApiRouting(auth) }

                assertEquals(HttpStatusCode.Forbidden, client.put("/api/v1/admin/users/$userId") {
                    bearerAuth(userSession.token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"renamed"}""")
                }.status)

                assertEquals(HttpStatusCode.Conflict, client.put("/api/v1/admin/users/$userId") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin"}""")
                }.status)
                assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/admin/users/$userId") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"x"}""")
                }.status)
                assertEquals(HttpStatusCode.NotFound, client.put("/api/v1/admin/users/999999") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"renamed"}""")
                }.status)

                val rename = client.put("/api/v1/admin/users/$userId") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"renamed"}""")
                }
                assertEquals(HttpStatusCode.NoContent, rename.status)
                val currentUser = client.get("/api/v1/me") { bearerAuth(userSession.token) }
                assertEquals(HttpStatusCode.OK, currentUser.status)
                assertTrue(currentUser.bodyAsText().contains("\"username\":\"renamed\""))

                val accounts = client.get("/api/v1/admin/users") { bearerAuth(adminToken) }.bodyAsText()
                assertTrue(accounts.contains("\"username\":\"renamed\""))
                assertTrue(!accounts.contains("\"username\":\"listener\""))
                assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"listener","password":"listener password for tests"}""")
                }.status)
                assertEquals(HttpStatusCode.OK, client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"renamed","password":"listener password for tests"}""")
                }.status)
            }
        }
    }

    @Test
    fun administratorsCanListAccountsAndChangeRolesSafely() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_user_administration"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val adminId = auth.users().single().id
            val userId = auth.createUser("listener", "listener password for tests")
            val userSession = auth.login("listener", "listener password for tests")!!

            testApplication {
                application { configureApiRouting(auth) }

                val adminLogin = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"a strong password for tests"}""")
                }
                val adminToken = extractToken(adminLogin.bodyAsText())

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/users") { bearerAuth(userSession.token) }.status)
                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/users/$userId") { bearerAuth(userSession.token) }.status)
                assertEquals(HttpStatusCode.Forbidden, client.put("/api/v1/admin/users/$userId/role") {
                    bearerAuth(userSession.token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role":"ADMIN"}""")
                }.status)

                val accounts = client.get("/api/v1/admin/users") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, accounts.status)
                assertTrue(accounts.bodyAsText().contains("\"username\":\"listener\",\"role\":\"USER\",\"enabled\":true"))
                assertTrue(!accounts.bodyAsText().contains("password"))
                val details = client.get("/api/v1/admin/users/$userId") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, details.status)
                assertTrue(details.bodyAsText().contains("\"username\":\"listener\""))
                assertTrue(details.bodyAsText().contains("\"libraryIds\":[]"))
                assertTrue(!details.bodyAsText().contains("password"))
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/admin/users/999999") { bearerAuth(adminToken) }.status)

                assertEquals(HttpStatusCode.Conflict, client.put("/api/v1/admin/users/$adminId/role") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role":"USER"}""")
                }.status)
                assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/admin/users/$userId/role") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role":"OWNER"}""")
                }.status)

                assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/admin/users/$userId/role") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role":"ADMIN"}""")
                }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(userSession.token) }.status)

                val promotedLogin = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"listener","password":"listener password for tests"}""")
                }
                val promotedToken = extractToken(promotedLogin.bodyAsText())
                assertEquals(HttpStatusCode.OK, client.get("/api/v1/admin/users") { bearerAuth(promotedToken) }.status)

                assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/admin/users/$adminId/role") {
                    bearerAuth(promotedToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"role":"USER"}""")
                }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(adminToken) }.status)
                assertFailsWith<LastEnabledAdministratorException> { auth.setUserRole(userId, "USER") }
                assertEquals("ADMIN", auth.users().single { it.id == userId }.role)
            }
        }
    }

    @Test
    fun loginCreatesBearerSessionWhichCanBeReadAndRevoked() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_http_auth"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val userId = auth.createUser("listener", "listener password for tests")
            val userSession = auth.login("listener", "listener password for tests")!!
            assertFailsWith<IllegalStateException> {
                auth.createInitialAdmin("second-admin", "another strong password")
            }

            testApplication {
                application { configureApiRouting(auth) }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me?token=not-a-session").status)
                val unknownUser = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"missing","password":"a strong password for tests"}""")
                }
                val wrongPassword = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"an incorrect password"}""")
                }
                assertEquals(HttpStatusCode.Unauthorized, unknownUser.status)
                assertEquals(unknownUser.bodyAsText(), wrongPassword.bodyAsText())

                val login = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"a strong password for tests"}""")
                }
                assertEquals(HttpStatusCode.OK, login.status)
                val token = extractToken(login.bodyAsText())
                assertEquals(43, token.length)
                dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT token_hash FROM user_session").use { statement ->
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            assertEquals(64, rows.getString(1).length)
                            assertNotEquals(token, rows.getString(1))
                        }
                    }
                }

                val me = client.get("/api/v1/me") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, me.status)
                assertTrue(me.bodyAsText().contains("\"username\":\"admin\""))
                assertTrue(me.bodyAsText().contains("\"role\":\"ADMIN\""))

                val logout = client.post("/api/v1/auth/logout") { bearerAuth(token) }
                assertEquals(HttpStatusCode.NoContent, logout.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(token) }.status)

                val secondLogin = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"a strong password for tests"}""")
                }
                val secondToken = extractToken(secondLogin.bodyAsText())
                val changePassword = client.put("/api/v1/me/password") {
                    bearerAuth(secondToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"currentPassword":"a strong password for tests","newPassword":"an even stronger replacement password"}""")
                }
                assertEquals(HttpStatusCode.NoContent, changePassword.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(secondToken) }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"a strong password for tests"}""")
                }.status)

                val reauthenticated = client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"an even stronger replacement password"}""")
                }
                assertEquals(HttpStatusCode.OK, reauthenticated.status)
                val adminToken = extractToken(reauthenticated.bodyAsText())

                val deniedAdminOperation = client.post("/api/v1/admin/users") {
                    bearerAuth(userSession.token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"other","password":"another strong password"}""")
                }
                assertEquals(HttpStatusCode.Forbidden, deniedAdminOperation.status)
                assertEquals(HttpStatusCode.Forbidden, client.put("/api/v1/admin/users/$userId/password") {
                    bearerAuth(userSession.token)
                    contentType(ContentType.Application.Json)
                    setBody("""{"password":"replacement password for tests"}""")
                }.status)
                val created = client.post("/api/v1/admin/users") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"newuser","password":"another strong password"}""")
                }
                assertEquals(HttpStatusCode.Created, created.status)
                val newUserId = auth.users().single { it.username == "newuser" }.id
                val newUserToken = extractToken(client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"newuser","password":"another strong password"}""")
                }.bodyAsText())
                assertEquals(HttpStatusCode.NoContent, client.put("/api/v1/admin/users/$newUserId/password") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"password":"replacement password for tests"}""")
                }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(newUserToken) }.status)
                assertEquals(HttpStatusCode.OK, client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"newuser","password":"replacement password for tests"}""")
                }.status)

                assertEquals(HttpStatusCode.OK, client.get("/api/v1/me") { bearerAuth(userSession.token) }.status)
                val disabled = client.put("/api/v1/admin/users/$userId/enabled") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"enabled":false}""")
                }
                assertEquals(HttpStatusCode.NoContent, disabled.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(userSession.token) }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"listener","password":"listener password for tests"}""")
                }.status)
            }
        }
    }

    @Test
    fun expiredSessionIsRejectedAfterItsExpiry() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_expiring_auth"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 1
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val auth = LocalAuthService(dataSource, Clock.fixed(now, Clock.systemUTC().zone), Duration.ofSeconds(30))
            auth.createInitialAdmin("admin", "a strong password for tests")
            val session = auth.login("admin", "a strong password for tests")!!
            val afterExpiry = LocalAuthService(
                dataSource,
                Clock.fixed(now.plusSeconds(31), Clock.systemUTC().zone),
                Duration.ofSeconds(30),
            )

            assertEquals(null, afterExpiry.authenticate(session.token))
        }
    }

    private fun extractToken(json: String): String = Regex("\"accessToken\":\"([^\"]+)\"")
        .find(json)!!.groupValues[1]
}

package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
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
import org.airsonic.player.security.LocalAuthService
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
                application { standaloneModule(auth) }

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
                val created = client.post("/api/v1/admin/users") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"newuser","password":"another strong password"}""")
                }
                assertEquals(HttpStatusCode.Created, created.status)

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

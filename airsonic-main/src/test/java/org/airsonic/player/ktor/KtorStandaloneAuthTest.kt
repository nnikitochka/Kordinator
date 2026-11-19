package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
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
            assertFailsWith<IllegalStateException> {
                auth.createInitialAdmin("second-admin", "another strong password")
            }

            testApplication {
                application { standaloneModule(auth) }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me").status)
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
                val token = Regex("\"accessToken\":\"([^\"]+)\"")
                    .find(login.bodyAsText())!!.groupValues[1]
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
}

package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.security.PasswordRecoveryEmailSender
import org.airsonic.player.security.StandalonePasswordRecoveryService
import org.airsonic.player.runtime.StandaloneDatabase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorStandalonePasswordRecoveryTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun passwordRecoveryUsesNeutralResponsesOneTimeTokensAndRevokesSessions() {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_recovery_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        }).use { dataSource ->
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val userId = auth.createUser("listener", "listener password for tests")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val activeUserToken = auth.login("listener", "listener password for tests")!!.token
            val deliveredToken = AtomicReference<String?>()
            val deliveredTo = AtomicReference<String?>()
            val emailSender = PasswordRecoveryEmailSender { recipient, _, body ->
                deliveredTo.set(recipient)
                deliveredToken.set(Regex("[A-Za-z0-9_-]{40,}").find(body)?.value)
            }
            val recovery = StandalonePasswordRecoveryService(auth, emailSender)
            val mapper = jacksonObjectMapper()

            testApplication {
                application { standaloneModule(authService = auth, passwordRecoveryService = recovery) }

                val setEmail = client.put("/api/v1/admin/users/$userId/email") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":" Listener@Example.com "}""")
                }
                assertEquals(204, setEmail.status.value)
                val userResponse = mapper.readTree(client.get("/api/v1/admin/users/$userId") {
                    bearerAuth(adminToken)
                }.bodyAsText())
                assertEquals("listener@example.com", userResponse.get("user").get("email").asText())

                val unknown = client.post("/api/v1/auth/password-recovery") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"missing@example.com"}""")
                }
                val known = client.post("/api/v1/auth/password-recovery") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"LISTENER@example.com"}""")
                }
                assertEquals(202, unknown.status.value)
                assertEquals(202, known.status.value)
                assertEquals(unknown.bodyAsText(), known.bodyAsText())
                val token = withTimeout(5_000) {
                    while (deliveredToken.get() == null) delay(10)
                    assertNotNull(deliveredToken.get())
                }
                assertEquals("listener@example.com", deliveredTo.get())

                val storedHash = dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT token_hash FROM password_recovery_token WHERE user_id = ?").use { statement ->
                        statement.setLong(1, userId)
                        statement.executeQuery().use { rows -> assertTrue(rows.next()); rows.getString(1) }
                    }
                }
                assertFalse(storedHash == token)
                assertEquals(64, storedHash.length)

                val reset = client.post("/api/v1/auth/password-recovery/reset") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"$token","newPassword":"a new strong password"}""")
                }
                assertEquals(204, reset.status.value)
                assertNull(auth.authenticate(activeUserToken))
                assertNotNull(auth.login("listener", "a new strong password"))
                assertEquals(400, client.post("/api/v1/auth/password-recovery/reset") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"$token","newPassword":"another strong password"}""")
                }.status.value)
            }
        }
    }
}

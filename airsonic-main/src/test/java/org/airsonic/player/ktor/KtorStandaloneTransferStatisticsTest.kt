package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.domain.User
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.StatusService
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneTransferStatisticsTest {
    @Test
    fun adminCanReadTransferAndUserStatistics() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_transfer_statistics"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val token = auth.login("admin", "a strong password for tests")!!.token
            val player = Player().apply { username = "admin"; type = "test-client"; name = "Test player" }
            val transfer = TransferStatus(player).apply {
                externalFile = Path.of("/music/private/song.mp3")
                addBytesTransferred(123)
            }
            val statuses = Mockito.mock(StatusService::class.java)
            Mockito.`when`(statuses.getAllStreamStatuses()).thenReturn(listOf(transfer))
            Mockito.`when`(statuses.getAllDownloadStatuses()).thenReturn(emptyList())
            Mockito.`when`(statuses.getAllUploadStatuses()).thenReturn(emptyList())
            Mockito.`when`(statuses.getActivePlays()).thenReturn(emptyList())
            Mockito.`when`(statuses.getInactivePlays()).thenReturn(emptyList())
            val security = Mockito.mock(SecurityService::class.java)
            Mockito.`when`(security.allUsers).thenReturn(listOf(User("admin", null).apply {
                bytesStreamed = 1234
                bytesDownloaded = 56
                bytesUploaded = 7
            }))

            testApplication {
                application { configureApiRouting(authService = auth, statusService = statuses, securityService = security) }

                val transfers = client.get("/api/v1/admin/transfer-statistics") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, transfers.status)
                assertEquals("no-store", transfers.headers["Cache-Control"])
                val body = transfers.bodyAsText()
                assertTrue(body.contains("\"type\":\"stream\""))
                assertTrue(body.contains("\"path\":\"song.mp3\""))
                assertTrue(body.contains("\"bytesTransferred\":123"))

                val users = client.get("/api/v1/admin/user-transfer-statistics") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, users.status)
                assertTrue(users.bodyAsText().contains("\"streamed\":1234"))

                val nowPlaying = client.get("/api/v1/now-playing") { bearerAuth(token) }
                assertEquals(HttpStatusCode.OK, nowPlaying.status)
                assertEquals("[]", nowPlaying.bodyAsText())
            }
        }
    }
}

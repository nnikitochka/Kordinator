package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.ServerInfoService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.VersionService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneServerInfoTest {
    @Test
    fun adminCanReadServerInfoLogAndDisableGettingStarted() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_server_info"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            auth.createUser("listener", "listener password for tests")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val listenerToken = auth.login("listener", "listener password for tests")!!.token
            val settings = Mockito.mock(SettingsService::class.java)
            Mockito.`when`(settings.getBrand()).thenReturn("Kordinator")
            Mockito.`when`(settings.getLogFile()).thenReturn("/tmp/kordinator-test-log-does-not-exist")
            Mockito.`when`(settings.isGettingStartedEnabled()).thenReturn(true)
            val versions = Mockito.mock(VersionService::class.java)
            val serverInfo = ServerInfoService(versions, settings)

            testApplication {
                application {
                    configureApiRouting(authService = auth, serverInfoService = serverInfo, settingsService = settings)
                }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/server-info") { bearerAuth(listenerToken) }.status)
                val info = client.get("/api/v1/admin/server-info") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, info.status)
                assertEquals("no-store", info.headers["Cache-Control"])
                assertTrue(info.bodyAsText().contains("Kordinator"))
                assertEquals(HttpStatusCode.OK, client.get("/api/v1/admin/server-log") { bearerAuth(adminToken) }.status)
                assertTrue(client.get("/api/v1/admin/getting-started") { bearerAuth(adminToken) }.bodyAsText().contains("\"enabled\":true"))
                assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/admin/getting-started") { bearerAuth(adminToken) }.status)
                Mockito.verify(settings).save()
            }
        }
    }
}

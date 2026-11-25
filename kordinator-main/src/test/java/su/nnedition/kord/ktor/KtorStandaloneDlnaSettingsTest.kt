package su.nnedition.kord.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.UPnPService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneDlnaSettingsTest {
    @Test
    fun dlnaSettingsAreAdminOnlyAndRestartTheMediaServerWhenSaved() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_dlna_settings"
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
            Mockito.`when`(settings.getDlnaServerName()).thenReturn("Kordinator")
            val upnp = Mockito.mock(UPnPService::class.java)

            testApplication {
                application { configureApiRouting(authService = auth, settingsService = settings, upnpService = upnp) }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/settings/dlna") { bearerAuth(listenerToken) }.status)
                val read = client.get("/api/v1/admin/settings/dlna") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, read.status)
                assertTrue(read.bodyAsText().contains("Kordinator"))

                val saved = client.put("/api/v1/admin/settings/dlna") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"enabled":true,"serverName":" Living Room ","baseLanUrl":" "}""")
                }
                assertEquals(HttpStatusCode.NoContent, saved.status)
                val calls = Mockito.inOrder(upnp, settings)
                calls.verify(upnp).setMediaServerEnabled(false)
                calls.verify(settings).save()
                calls.verify(upnp).setMediaServerEnabled(true)
            }
        }
    }
}

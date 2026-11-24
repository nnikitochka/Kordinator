package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.UserSettings
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.SettingsService
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorStandaloneUserSettingsTest {
    @Test
    fun settingsBelongToAuthenticatedUserAndUpdateValidatesBeforeSaving() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_user_settings"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val settings = Mockito.mock(SettingsService::class.java)
            val userSettings = UserSettings("admin")
            userSettings.showSideBar = true
            Mockito.`when`(settings.getUserSettings("admin")).thenReturn(userSettings)

            testApplication {
                application { configureApiRouting(authService = auth, settingsService = settings) }

                assertEquals(HttpStatusCode.OK, client.get("/api/v1/me/settings") { bearerAuth(adminToken) }.status)
                val updated = client.patch("/api/v1/me/settings") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"nowPlayingAllowed":true,"paginationSizeFiles":40,"listenBrainzUrl":"https://listen.example"}""")
                }
                assertEquals(HttpStatusCode.OK, updated.status)
                assertTrue(userSettings.nowPlayingAllowed)
                assertEquals(40, userSettings.paginationSizeFiles)
                assertEquals("https://listen.example", userSettings.listenBrainzUrl)
                assertTrue(updated.bodyAsText().contains("\"nowPlayingAllowed\":true"))
                Mockito.verify(settings).updateUserSettings(userSettings)

                val invalid = client.patch("/api/v1/me/settings") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"showSideBar":false,"paginationSizeFiles":0}""")
                }
                assertEquals(HttpStatusCode.BadRequest, invalid.status)
                assertTrue(userSettings.showSideBar)
                Mockito.verify(settings, Mockito.times(1)).updateUserSettings(userSettings)
                assertFalse(invalid.bodyAsText().isEmpty())
            }
        }
    }
}

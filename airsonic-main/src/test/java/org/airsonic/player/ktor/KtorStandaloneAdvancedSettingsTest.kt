package org.airsonic.player.ktor

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
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.SettingsService
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorStandaloneAdvancedSettingsTest {
    @Test
    fun advancedSettingsAreAdminOnlyAndNeverReturnSecrets() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_advanced_settings"
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
            Mockito.`when`(settings.brand).thenReturn("Kordinator")

            testApplication {
                application { standaloneModule(authService = auth, settingsService = settings) }

                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/settings/advanced") { bearerAuth(listenerToken) }.status)
                val read = client.get("/api/v1/admin/settings/advanced") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, read.status)
                assertTrue(read.bodyAsText().contains("Kordinator"))
                assertFalse(read.bodyAsText().contains("ldapManagerPassword"))
                assertFalse(read.bodyAsText().contains("smtpPassword"))
                assertFalse(read.bodyAsText().contains("recaptchaSecretKey"))

                val saved = client.put("/api/v1/admin/settings/advanced") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{
                        "downloadLimit":1000,"uploadLimit":2000,
                        "ldapUrl":"","ldapSearchFilter":"","ldapManagerDn":"",
                        "smtpServer":"mail.example","smtpEncryption":"STARTTLS","smtpPort":"587",
                        "smtpUser":"music","smtpFrom":"music@example","recaptchaSiteKey":"site",
                        "ldapEnabled":false,"ldapAutoShadowing":false,"captchaEnabled":false,
                        "smtpPassword":"new-secret"
                    }""")
                }
                assertEquals(HttpStatusCode.NoContent, saved.status)
                Mockito.verify(settings).save()
            }
        }
    }
}

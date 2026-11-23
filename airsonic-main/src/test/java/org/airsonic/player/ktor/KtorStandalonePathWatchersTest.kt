package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.PathWatcherService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandalonePathWatchersTest {
    @Test
    fun adminCanReadPathWatchers() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_path_watchers"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        val watchers = PathWatcherService()
        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("admin", "a strong password for tests")
                auth.createUser("listener", "listener password for tests")
                val adminToken = auth.login("admin", "a strong password for tests")!!.token
                val listenerToken = auth.login("listener", "listener password for tests")!!.token

                testApplication {
                    application {
                        standaloneModule(authService = auth, pathWatcherService = watchers)
                    }

                    val response = client.get("/api/v1/admin/path-watchers") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("[]", response.bodyAsText())
                    assertEquals(
                        HttpStatusCode.Forbidden,
                        client.get("/api/v1/admin/path-watchers") { bearerAuth(listenerToken) }.status,
                    )
                }
            }
        } finally {
            watchers.close()
        }
    }
}

package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.airsonic.player.runtime.RuntimeCompositionRoot
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorStandaloneCacheStatisticsTest {
    @Test
    fun adminCanInspectAndClearCaches() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_cache_statistics"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })
        val runtime = RuntimeCompositionRoot()
        val caches = runtime.createCacheManager()
        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("admin", "a strong password for tests")
                val token = auth.login("admin", "a strong password for tests")!!.token
                val cache = caches.getCache<String, String>("userCache")!!
                cache.put("test", "value")

                testApplication {
                    application {
                        configureApiRouting(
                            authService = auth,
                            cacheManager = caches,
                            meterRegistry = SimpleMeterRegistry(),
                        )
                    }

                    val snapshot = client.get("/api/v1/admin/cache-statistics") { bearerAuth(token) }
                    assertEquals(HttpStatusCode.OK, snapshot.status)
                    assertEquals("no-store", snapshot.headers["Cache-Control"])
                    assertTrue(snapshot.bodyAsText().contains("userCache"))

                    val cleared = client.delete("/api/v1/admin/cache-statistics/userCache") { bearerAuth(token) }
                    assertEquals(HttpStatusCode.NoContent, cleared.status)
                    assertFalse(cache.containsKey("test"))
                    assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/admin/cache-statistics/missing") { bearerAuth(token) }.status)
                }
            }
        } finally {
            caches.close()
        }
    }
}

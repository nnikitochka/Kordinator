package su.nnedition.kord.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import su.nnedition.kord.health.DefaultHealthContributorRegistry
import su.nnedition.kord.health.Health
import su.nnedition.kord.health.SimpleStatusAggregator
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneHealthTest {
    @Test
    fun adminHealthIncludesContributorDetailsAndRejectsRegularUsers() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_admin_health"
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
            val userToken = auth.login("listener", "listener password for tests")!!.token
            val contributors = DefaultHealthContributorRegistry().apply {
                registerContributor("database", su.nnedition.kord.health.HealthIndicator {
                    Health.up().withDetail("connection", "ready").build()
                })
                registerContributor("cache", su.nnedition.kord.health.HealthIndicator { Health.down().build() })
            }

            testApplication {
                application {
                    configureApiRouting(
                        authService = auth,
                        healthContributors = contributors,
                        healthStatusAggregator = SimpleStatusAggregator(listOf("DOWN", "UP", "UNKNOWN")),
                    )
                }

                val publicHealth = client.get("/api/v1/health")
                assertEquals(HttpStatusCode.OK, publicHealth.status)
                assertTrue(!publicHealth.bodyAsText().contains("connection"))
                assertEquals(HttpStatusCode.NotFound, client.get("/rest/getServerHealth") { bearerAuth(adminToken) }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/admin/health").status)
                assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/health") { bearerAuth(userToken) }.status)
                val result = client.get("/api/v1/admin/health") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, result.status)
                assertEquals("no-store", result.headers["Cache-Control"])
                val body = result.bodyAsText()
                assertTrue(body.contains("\"status\":\"DOWN\""))
                assertTrue(body.contains("\"name\":\"database\""))
                assertTrue(body.contains("\"connection\":\"ready\""))
            }
        }
    }
}

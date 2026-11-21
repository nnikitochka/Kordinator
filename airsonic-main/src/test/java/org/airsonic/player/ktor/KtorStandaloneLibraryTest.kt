package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import java.nio.file.Files
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneLibraryTest {
    @TempDir
    lateinit var musicRoot: Path

    @Test
    fun administratorCanRegisterAndListReadableLibraryRoots() {
        val defaultRoot = musicRoot.resolveSibling("${musicRoot.fileName}-default")
        Files.createDirectories(defaultRoot)
        val runtime = KtorRuntimeFixture(musicRoot.resolveSibling("runtime"), defaultRoot, "standalone_library_core_${musicRoot.fileName}")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_library_api_${musicRoot.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        try {
            dataSource.use {
                StandaloneDatabase.migrate(dataSource)
                val auth = LocalAuthService(dataSource)
                auth.createInitialAdmin("admin", "a strong password for tests")
                auth.createUser("listener", "listener password for tests")
                val adminToken = auth.login("admin", "a strong password for tests")!!.token
                val userToken = auth.login("listener", "listener password for tests")!!.token

                testApplication {
                    application { standaloneModule(authService = auth, mediaFolderService = runtime.mediaFolderService) }

                    val created = client.post("/api/v1/admin/libraries") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Other","path":"$musicRoot"}""")
                }
                    assertEquals(HttpStatusCode.Created, created.status)
                    assertTrue(created.bodyAsText().contains("\"name\":\"Other\""))
                    assertTrue(created.bodyAsText().contains(musicRoot.toRealPath().toString()))
                    assertTrue(created.bodyAsText().contains("\"enabled\":true"))
                    val createdId = Regex("\\\"id\\\":(\\d+)").find(created.bodyAsText())!!.groupValues[1]

                    val deniedEdit = client.put("/api/v1/admin/libraries/$createdId") {
                        bearerAuth(userToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Renamed"}""")
                    }
                    assertEquals(HttpStatusCode.Forbidden, deniedEdit.status)

                    val duplicateName = client.put("/api/v1/admin/libraries/$createdId") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Music"}""")
                    }
                    assertEquals(HttpStatusCode.Conflict, duplicateName.status)

                    val renamed = client.put("/api/v1/admin/libraries/$createdId") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"  Renamed  "}""")
                    }
                    assertEquals(HttpStatusCode.OK, renamed.status)
                    assertTrue(renamed.bodyAsText().contains("\"name\":\"Renamed\""))

                    val missingLibrary = client.put("/api/v1/admin/libraries/999999") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Missing"}""")
                    }
                    assertEquals(HttpStatusCode.NotFound, missingLibrary.status)

                    val listed = client.get("/api/v1/admin/libraries") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, listed.status)
                    assertTrue(listed.bodyAsText().contains("\"id\""))
                    assertTrue(listed.bodyAsText().contains("\"name\":\"Music\""))
                    assertTrue(listed.bodyAsText().contains("\"name\":\"Renamed\""))

                    val denied = client.get("/api/v1/admin/libraries") { bearerAuth(userToken) }
                    assertEquals(HttpStatusCode.Forbidden, denied.status)

                    val duplicate = client.post("/api/v1/admin/libraries") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Other","path":"$musicRoot"}""")
                }
                    assertEquals(HttpStatusCode.Conflict, duplicate.status)
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/admin/music-folders") { bearerAuth(adminToken) }.status)
                }
            }
        } finally {
            runtime.close()
        }
    }
}

package su.nnedition.kord.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.service.library.scan.LibraryScan
import su.nnedition.kord.service.library.scan.StandaloneLibraryScanService
import org.mockito.Mockito
import java.nio.file.Files
import org.junit.jupiter.api.io.TempDir
import su.nnedition.kord.api.configureApiRouting
import java.nio.file.Path
import java.time.Instant
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
        val marker = musicRoot.resolve("keep-after-library-rename.txt")
        Files.writeString(marker, "library data stays in place")
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
                val listenerId = requireNotNull(auth.authenticate(userToken)).id
                val scans = Mockito.mock(StandaloneLibraryScanService::class.java)
                val movedRoot = Files.createDirectories(musicRoot.resolveSibling("moved-${musicRoot.fileName}"))

                testApplication {
                    application { configureApiRouting(authService = auth, mediaFolderService = runtime.mediaFolderService, libraryScanService = scans) }

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

                    auth.setAllowedLibraries(listenerId, setOf(createdId.toLong()))
                    assertEquals(setOf(createdId.toLong()), auth.allowedLibraries(listenerId))

                    val accessibleLibraries = client.get("/api/v1/libraries") { bearerAuth(userToken) }
                    assertEquals(HttpStatusCode.OK, accessibleLibraries.status)
                    assertTrue(accessibleLibraries.bodyAsText().contains("\"id\":$createdId"))
                    assertTrue(accessibleLibraries.bodyAsText().contains("\"name\":\"Other\""))
                    assertTrue(!accessibleLibraries.bodyAsText().contains(musicRoot.toRealPath().toString()))

                    val renamed = client.put("/api/v1/admin/libraries/$createdId") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"  Renamed  "}""")
                    }
                    assertEquals(HttpStatusCode.OK, renamed.status)
                    assertTrue(renamed.bodyAsText().contains("\"name\":\"Renamed\""))
                    assertTrue(renamed.bodyAsText().contains("\"id\":$createdId"))
                    assertTrue(renamed.bodyAsText().contains(musicRoot.toRealPath().toString()))
                    assertEquals("library data stays in place", Files.readString(marker))

                    val renamedAgain = client.put("/api/v1/admin/libraries/$createdId") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Renamed again"}""")
                    }
                    assertEquals(HttpStatusCode.OK, renamedAgain.status)
                    assertEquals(setOf(createdId.toLong()), auth.allowedLibraries(listenerId))

                    Mockito.`when`(scans.enqueue(createdId.toLong())).thenReturn(
                        LibraryScan("path-change-scan", createdId.toLong(), "QUEUED", 0, 0, Instant.now(), null),
                    )
                    val moved = client.put("/api/v1/admin/libraries/$createdId") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Renamed again","path":"$movedRoot"}""")
                    }
                    assertEquals(HttpStatusCode.Accepted, moved.status, moved.bodyAsText())
                    assertTrue(moved.bodyAsText().contains("\"scanId\":\"path-change-scan\""))
                    assertEquals(movedRoot.toRealPath(), runtime.mediaFolderService.getMusicFolderById(createdId.toInt(), true, true)!!.path)

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
                    assertTrue(listed.bodyAsText().contains("\"name\":\"Renamed again\""))

                    val denied = client.get("/api/v1/admin/libraries") { bearerAuth(userToken) }
                    assertEquals(HttpStatusCode.Forbidden, denied.status)

                    val duplicate = client.post("/api/v1/admin/libraries") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Other","path":"$movedRoot"}""")
                }
                    assertEquals(HttpStatusCode.Conflict, duplicate.status)
                    assertEquals(setOf(createdId.toLong()), auth.allowedLibraries(listenerId))

                    val deletedAsListener = client.delete("/api/v1/admin/libraries/$createdId") { bearerAuth(userToken) }
                    assertEquals(HttpStatusCode.Forbidden, deletedAsListener.status)
                    val deleted = client.delete("/api/v1/admin/libraries/$createdId") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.NoContent, deleted.status)
                    assertTrue(auth.allowedLibraries(listenerId).orEmpty().isEmpty())
                    assertTrue(runtime.mediaFolderService.getMusicFolderById(createdId.toInt(), true, true) == null)
                    assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/admin/music-folders") { bearerAuth(adminToken) }.status)

                    val initialLibraryId = requireNotNull(
                        runtime.mediaFolderService.getAllMusicFolders(true, true, true).singleOrNull { it.name == "Music" }?.id,
                    )
                    assertEquals(
                        HttpStatusCode.NoContent,
                        client.delete("/api/v1/admin/libraries/$initialLibraryId") { bearerAuth(adminToken) }.status,
                    )
                    assertEquals(
                        HttpStatusCode.NotFound,
                        client.delete("/api/v1/admin/libraries/0") { bearerAuth(adminToken) }.status,
                    )
                }
            }
        } finally {
            runtime.close()
        }
    }
}

package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import java.nio.file.Path
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class KtorStandaloneVideoTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun listsVideosOnlyFromLibrariesAssignedToTheCurrentUser() {
        val musicRoot = home.resolve("music")
        Files.createDirectories(musicRoot)
        val runtime = KtorRuntimeFixture(home.resolve("runtime"), musicRoot, "standalone_videos_core_${home.fileName}")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_videos_api_${home.fileName}"
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
                val listenerId = auth.createUser("listener", "listener password for tests")
                val adminToken = auth.login("admin", "a strong password for tests")!!.token
                val listenerToken = auth.login("listener", "listener password for tests")!!.token
                val library = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
                val changed = Instant.now()
                listOf("Concert A", "Concert B").forEachIndexed { index, title ->
                    val video = MediaFile().apply {
                        path = "video-$index.mp4"
                        folderId = library.id
                        mediaType = MediaFile.MediaType.VIDEO
                        format = "mp4"
                        this.title = title
                        artist = "Live Artist"
                        duration = 90.0 + index
                        fileSize = 1_000L + index
                        width = 1920
                        height = 1080
                        parentPath = ""
                        created = changed
                        this.changed = changed
                        lastScanned = changed
                        childrenLastUpdated = changed
                        setPresent(true)
                    }
                    runtime.runtime.createPersistenceGraph().daos.mediaFileDao.createOrUpdateMediaFile(video) { }
                }
                val mediaFileDao = runtime.runtime.createPersistenceGraph().daos.mediaFileDao

                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaFileDao = mediaFileDao,
                        )
                    }

                    val hidden = client.get("/api/v1/videos") { bearerAuth(listenerToken) }
                    assertEquals(HttpStatusCode.OK, hidden.status)
                    assertEquals(0, jacksonObjectMapper().readTree(hidden.bodyAsText()).get("items").size())

                    val first = client.get("/api/v1/videos?limit=1") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, first.status)
                    val firstBody = jacksonObjectMapper().readTree(first.bodyAsText())
                    assertEquals(1, firstBody.get("items").size())
                    assertEquals("Concert A", firstBody.get("items")[0].get("title").asText())
                    assertEquals(1920, firstBody.get("items")[0].get("width").asInt())
                    assertTrue(firstBody.get("hasMore").asBoolean())

                    assertTrue(auth.setAllowedLibraries(listenerId, setOf(library.id!!.toLong())))
                    val visible = client.get("/api/v1/videos") { bearerAuth(listenerToken) }
                    assertEquals(2, jacksonObjectMapper().readTree(visible.bodyAsText()).get("items").size())

                    val invalidLimit = client.get("/api/v1/videos?limit=bad") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidLimit.status)
                    val invalidOffset = client.get("/api/v1/videos?offset=-1") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidOffset.status)
                    assertFalse(jacksonObjectMapper().readTree(invalidLimit.bodyAsText()).get("message").asText().isBlank())
                }
            }
        } finally {
            runtime.close()
        }
    }
}

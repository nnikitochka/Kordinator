package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.StandaloneVideoPlaybackService
import org.airsonic.player.service.StandaloneVideoBookmarkService
import org.airsonic.player.service.TranscodingService
import org.mockito.Mockito
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
                val videos = listOf("Concert A", "Concert B").mapIndexed { index, title ->
                    MediaFile().apply {
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
                    }.also { video -> runtime.runtime.createPersistenceGraph().daos.mediaFileDao.createOrUpdateMediaFile(video) { } }
                }
                val mediaFileDao = runtime.runtime.createPersistenceGraph().daos.mediaFileDao
                val mediaFileService = Mockito.mock(MediaFileService::class.java)
                videos.forEach { video -> Mockito.`when`(mediaFileService.getMediaFile(video.id!!)).thenReturn(video) }
                val playbackService = StandaloneVideoPlaybackService(mediaFileService, Mockito.mock(TranscodingService::class.java))
                val videoBookmarkService = StandaloneVideoBookmarkService(dataSource, mediaFileService)
                val previousHome = System.getProperty("airsonic.home")
                System.setProperty("airsonic.home", home.resolve("airsonic-home").toString())

                try {
                testApplication {
                    application {
                        standaloneModule(
                            authService = auth,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaFileDao = mediaFileDao,
                            mediaFileService = mediaFileService,
                            videoPlaybackService = playbackService,
                            videoBookmarkService = videoBookmarkService,
                        )
                    }

                    val hidden = client.get("/api/v1/videos") { bearerAuth(listenerToken) }
                    assertEquals(HttpStatusCode.OK, hidden.status)
                    assertEquals(0, jacksonObjectMapper().readTree(hidden.bodyAsText()).get("items").size())
                    val videoId = requireNotNull(videos.first().id)
                    val hiddenPlayback = client.post("/api/v1/videos/$videoId/playback-sessions") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    assertEquals(HttpStatusCode.NotFound, hiddenPlayback.status)

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

                    val unauthenticatedBookmarks = client.get("/api/v1/me/video-bookmarks")
                    assertEquals(HttpStatusCode.Unauthorized, unauthenticatedBookmarks.status)
                    val hiddenBookmark = client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(adminToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":10000}""")
                    }
                    assertEquals(HttpStatusCode.NoContent, hiddenBookmark.status)
                    val privateBookmarks = client.get("/api/v1/me/video-bookmarks") { bearerAuth(listenerToken) }
                    assertEquals(0, jacksonObjectMapper().readTree(privateBookmarks.bodyAsText()).get("count").asInt())
                    val savedBookmark = client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":10000,"comment":"resume here"}""")
                    }
                    assertEquals(HttpStatusCode.NoContent, savedBookmark.status)
                    val bookmarkList = jacksonObjectMapper().readTree(
                        client.get("/api/v1/me/video-bookmarks") { bearerAuth(listenerToken) }.bodyAsText(),
                    )
                    assertEquals(1, bookmarkList.get("count").asInt())
                    assertEquals(videoId, bookmarkList.get("items")[0].get("videoId").asInt())
                    assertEquals("resume here", bookmarkList.get("items")[0].get("comment").asText())
                    assertEquals(HttpStatusCode.BadRequest, client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":-1}""")
                    }.status)
                    val nearEnd = client.put("/api/v1/videos/$videoId/bookmark") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"positionMillis":40000}""")
                    }
                    assertEquals(HttpStatusCode.NoContent, nearEnd.status)
                    assertEquals(0, jacksonObjectMapper().readTree(
                        client.get("/api/v1/me/video-bookmarks") { bearerAuth(listenerToken) }.bodyAsText(),
                    ).get("count").asInt())

                    val createdSession = client.post("/api/v1/videos/$videoId/playback-sessions") {
                        bearerAuth(listenerToken)
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                    assertEquals(HttpStatusCode.Created, createdSession.status, createdSession.bodyAsText())
                    val sessionJson = jacksonObjectMapper().readTree(createdSession.bodyAsText())
                    val playlistUrl = sessionJson.get("playlistUrl").asText()
                    val playlist = client.get(playlistUrl) { bearerAuth(listenerToken) }
                    assertEquals(HttpStatusCode.OK, playlist.status)
                    assertTrue(playlist.bodyAsText().contains("segments/0.ts"))
                    val outOfRangeSegment = client.get("/api/v1/videos/$videoId/playback-sessions/${sessionJson.get("id").asText()}/segments/99.ts") {
                        bearerAuth(listenerToken)
                    }
                    assertEquals(HttpStatusCode.BadRequest, outOfRangeSegment.status)
                    assertEquals(HttpStatusCode.NotFound, client.get(playlistUrl) { bearerAuth(adminToken) }.status)
                    assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/videos/$videoId/playback-sessions/${sessionJson.get("id").asText()}") {
                        bearerAuth(listenerToken)
                    }.status)

                    val invalidLimit = client.get("/api/v1/videos?limit=bad") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidLimit.status)
                    val invalidOffset = client.get("/api/v1/videos?offset=-1") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidOffset.status)
                    assertFalse(jacksonObjectMapper().readTree(invalidLimit.bodyAsText()).get("message").asText().isBlank())
                }
                } finally {
                    playbackService.close()
                    if (previousHome == null) System.clearProperty("airsonic.home") else System.setProperty("airsonic.home", previousHome)
                }
            }
        } finally {
            runtime.close()
        }
    }
}

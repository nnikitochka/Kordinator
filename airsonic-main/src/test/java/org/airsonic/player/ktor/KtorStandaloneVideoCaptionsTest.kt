package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.controller.captions.CaptionInfo
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.CaptionService
import org.airsonic.player.service.MediaFileService
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorStandaloneVideoCaptionsTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun listsAndStreamsVideoCaptionsOnlyForAccessibleLibraries() {
        val musicRoot = home.resolve("music")
        Files.createDirectories(musicRoot)
        Files.writeString(musicRoot.resolve("video.mp4"), "video")
        val subtitle = musicRoot.resolve("captions.srt")
        Files.writeString(subtitle, "1\n00:00:00,000 --> 00:00:01,000\nHello\n")
        val runtime = KtorRuntimeFixture(home.resolve("runtime"), musicRoot, "standalone_video_captions_core_${home.fileName}")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_video_captions_api_${home.fileName}"
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
                val listenerToken = auth.login("listener", "listener password for tests")!!.token
                val folder = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
                val video = MediaFile().apply {
                    id = 27
                    path = "video.mp4"
                    folderId = folder.id
                    mediaType = MediaFile.MediaType.VIDEO
                    title = "Live show"
                    parentPath = ""
                    present = true
                }
                val mediaFiles = Mockito.mock(MediaFileService::class.java)
                Mockito.`when`(mediaFiles.getMediaFile(27)).thenReturn(video)
                val captionService = Mockito.mock(CaptionService::class.java)
                val available = listOf(
                    CaptionInfo(subtitle.toString(), CaptionInfo.Location.EXTERNAL, "srt", "English"),
                )
                Mockito.`when`(captionService.listCaptions(video)).thenReturn(available)
                Mockito.`when`(captionService.openExternal(subtitle, "srt")).thenReturn(
                    ByteArrayInputStream(Files.readAllBytes(subtitle)),
                )

                testApplication {
                    application {
                        configureApiRouting(
                            authService = auth,
                            mediaFolderService = runtime.mediaFolderService,
                            mediaFileService = mediaFiles,
                            captionService = captionService,
                        )
                    }

                    val hidden = client.get("/api/v1/videos/27/captions") { bearerAuth(listenerToken) }
                    assertEquals(HttpStatusCode.NotFound, hidden.status)

                    val listed = client.get("/api/v1/videos/27/captions") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, listed.status)
                    val response = jacksonObjectMapper().readTree(listed.bodyAsText())
                    val token = response[0].get("id").asText()
                    assertEquals("external", response[0].get("location").asText())
                    assertEquals("srt", response[0].get("format").asText())
                    assertFalse(listed.bodyAsText().contains(subtitle.toString()))
                    assertFalse(listed.bodyAsText().contains("legacy-private-url"))

                    val content = client.get("/api/v1/videos/27/captions/$token") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, content.status)
                    assertTrue(content.bodyAsText().contains("Hello"))
                    assertEquals("text/plain", content.headers[HttpHeaders.ContentType]?.substringBefore(';'))

                    val notFound = client.get("/api/v1/videos/27/captions/unknown") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.NotFound, notFound.status)
                    val invalidVideo = client.get("/api/v1/videos/0/captions") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.BadRequest, invalidVideo.status)

                    val head = client.head("/api/v1/videos/27/captions/$token") { bearerAuth(adminToken) }
                    assertEquals(HttpStatusCode.OK, head.status)
                    assertEquals("no-store", head.headers[HttpHeaders.CacheControl])
                }
            }
        } finally {
            runtime.close()
        }
    }
}

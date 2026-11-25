package su.nnedition.kord.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.CallerContext
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.TagEditService
import su.nnedition.kord.service.TrackTagUpdate
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import su.nnedition.kord.api.configureApiRouting
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorStandaloneTrackTagTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun tagEditingRequiresAdministratorAndUsesTheAuthenticatedCaller() {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:ktor_track_tags_${home.fileName}"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        }).use { dataSource ->
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            auth.createUser("listener", "another strong password")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val listenerToken = auth.login("listener", "another strong password")!!.token

            val folders = Mockito.mock(MediaFolderService::class.java)
            val folder = Mockito.mock(MusicFolder::class.java)
            Mockito.`when`(folder.id).thenReturn(1)
            Mockito.`when`(folders.getAllMusicFolders(false, false)).thenReturn(listOf(folder))
            val file = Mockito.mock(MediaFile::class.java)
            Mockito.`when`(file.isFile).thenReturn(true)
            val mediaFiles = Mockito.mock(MediaFileService::class.java)
            Mockito.`when`(mediaFiles.getMediaFile(42)).thenReturn(file)
            var capturedData: TrackTagUpdate? = null
            var capturedCaller: CallerContext? = null
            val tags = object : TagEditService(
                Mockito.mock(su.nnedition.kord.service.metadata.MetaDataParserFactory::class.java),
                mediaFiles,
                folders,
                Mockito.mock(su.nnedition.kord.service.SecurityService::class.java),
            ) {
                override fun setTags(data: TrackTagUpdate, callerContext: CallerContext): String {
                    capturedData = data
                    capturedCaller = callerContext
                    return "UPDATED"
                }
            }

            testApplication {
                application {
                    configureApiRouting(
                        authService = auth,
                        mediaFileService = mediaFiles,
                        mediaFolderService = folders,
                        tagEditService = tags,
                    )
                }

                assertEquals(403, client.put("/api/v1/tracks/42/tags") {
                    bearerAuth(listenerToken)
                    setBody("""{"title":"forbidden"}""")
                }.status.value)
                assertEquals(400, client.put("/api/v1/tracks/nope/tags") {
                    bearerAuth(adminToken)
                    setBody("{}")
                }.status.value)
                val updated = client.put("/api/v1/tracks/42/tags") {
                    bearerAuth(adminToken)
                    setBody("""{"artist":"Artist","album":"Album","title":"Title","genre":"Rock","track":2,"year":2024}""")
                }
                assertEquals(200, updated.status.value, updated.bodyAsText())
                assertEquals("UPDATED", jacksonObjectMapper().readTree(updated.bodyAsText()).get("result").asText())
                assertEquals("Title", capturedData?.title)
                assertEquals(2, capturedData?.track)
                assertEquals(true, capturedCaller?.isAuthenticated)
                assertEquals(true, capturedCaller?.hasFolderAccess(1))
            }
        }
    }
}

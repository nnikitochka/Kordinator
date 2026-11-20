package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.RuntimeCompositionRoot
import org.airsonic.player.runtime.RuntimeConfiguration
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.MediaScannerService
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.junit.jupiter.api.io.TempDir

class KtorMusicFolderAdminRoutesTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun folderManagementUsesTheExistingMediaFolderServiceAndRequiresAdmin() {
        val mediaRoot = temporaryDirectory.resolve("music")
        java.nio.file.Files.createDirectories(mediaRoot)
        val secondRoot = temporaryDirectory.resolve("other-music")
        java.nio.file.Files.createDirectories(secondRoot)
        val legacyRuntime = RuntimeCompositionRoot(
            RuntimeConfiguration(
                mapOf(
                    "airsonic.home" to temporaryDirectory.resolve("airsonic").toString(),
                    "spring.datasource.url" to "jdbc:hsqldb:mem:ktor_music_folder_admin_${temporaryDirectory.fileName};hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false",
                    "spring.datasource.driver-class-name" to "org.hsqldb.jdbc.JDBCDriver",
                    "spring.datasource.username" to "sa",
                    "spring.datasource.password" to "",
                    "spring.liquibase.parameters.defaultMusicFolder" to mediaRoot.toString(),
                    "spring.liquibase.parameters.mysqlVarcharLimit" to "255",
                ),
            ),
        )
        val standaloneDataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:ktor_music_folder_admin_auth"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        try {
            legacyRuntime.createPersistenceGraph()
            val folders = legacyRuntime.createMediaFolderService()
            StandaloneDatabase.migrate(standaloneDataSource)
            val auth = LocalAuthService(standaloneDataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            auth.createUser("listener", "listener password for tests")
            val adminToken = auth.login("admin", "a strong password for tests")!!.token
            val listenerToken = auth.login("listener", "listener password for tests")!!.token
            val scanner = Mockito.mock(MediaScannerService::class.java)

            testApplication {
                application {
                    standaloneModule(authService = auth, mediaFolderService = folders, mediaScannerService = scanner)
                }

                val initial = client.get("/api/v1/admin/music-folders") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, initial.status)
                assertTrue(initial.bodyAsText().contains("\"name\":\"Music\""))
                assertTrue(initial.bodyAsText().contains(mediaRoot.toRealPath().toString()))

                val created = client.post("/api/v1/admin/music-folders") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Other","path":"$secondRoot"}""")
                }
                assertEquals(HttpStatusCode.Created, created.status)
                assertTrue(created.bodyAsText().contains("\"name\":\"Other\""))

                val listed = client.get("/api/v1/admin/music-folders") { bearerAuth(adminToken) }
                assertTrue(listed.bodyAsText().contains("\"name\":\"Music\""))
                assertTrue(listed.bodyAsText().contains("\"name\":\"Other\""))

                val denied = client.get("/api/v1/admin/music-folders") { bearerAuth(listenerToken) }
                assertEquals(HttpStatusCode.Forbidden, denied.status)

                val scanStarted = client.post("/api/v1/admin/music-scan") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.Accepted, scanStarted.status)
                Mockito.verify(scanner).scanLibrary()

                val scanStatus = client.get("/api/v1/admin/music-scan") { bearerAuth(adminToken) }
                assertEquals(HttpStatusCode.OK, scanStatus.status)
                assertTrue(scanStatus.bodyAsText().contains("\"scannedFiles\":0"))

                val overlap = client.post("/api/v1/admin/music-folders") {
                    bearerAuth(adminToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Duplicate","path":"$mediaRoot"}""")
                }
                assertEquals(HttpStatusCode.Conflict, overlap.status)
            }
        } finally {
            legacyRuntime.lifecycleRegistry.shutdown()
            standaloneDataSource.close()
        }
    }
}

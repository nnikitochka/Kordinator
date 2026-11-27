package su.nnedition.kord.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.FieldKey
import su.nnedition.kord.api.configureApiRouting
import su.nnedition.kord.api.support.kordinatorCatalogId
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.model.ReleaseDate
import su.nnedition.kord.runtime.StandaloneDatabase
import su.nnedition.kord.security.LocalAuthService
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.*

class KtorReleaseDateTest {
    @TempDir lateinit var home: Path

    private var oldHome: String? = null

    @org.junit.jupiter.api.BeforeEach fun setHome() {
        oldHome = System.setProperty("kordinator.home", home.resolve("runtime").toString())
        su.nnedition.kord.service.ConfigurationPropertiesService.reset()
    }

    @org.junit.jupiter.api.AfterEach fun restoreHome() {
        if (oldHome == null) System.clearProperty("kordinator.home") else System.setProperty("kordinator.home", oldHome)
    }

    @Test fun catalogPersistsDatesAndUsesTheSameAlbumVoteBeforePaginationAndYearFiltering() {
        val music = Files.createDirectory(home.resolve("music"))
        KtorRuntimeFixture(home.resolve("runtime"), music, "dates_${home.fileName}").use { runtime ->
            val ds = runtime.coreDataSource
            StandaloneDatabase.migrate(ds)
            val folder = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
            val dao = runtime.runtime.createPersistenceGraph().daos.mediaFileDao
            fun add(album: String, date: String?): MediaFile {
                val now = Instant.now()
                return MediaFile().apply {
                    path = "$album/${java.util.UUID.randomUUID()}.mp3"
                    folderId = folder.id
                    mediaType = MediaFile.MediaType.MUSIC
                    title = "Title"
                    artist = "Artist"
                    albumArtist = "Artist"
                    albumName = album
                    releaseDate = ReleaseDate.parse(date)
                    year = releaseDate?.year
                    duration = 1.0
                    fileSize = 1
                    parentPath = album
                    created = now
                    changed = now
                    lastScanned = now
                    childrenLastUpdated = now
                    present = true
                }.also { dao.createOrUpdateMediaFile(it) {} }
            }
            val first = add("Majority", "2026-02-27")
            add("Majority", "2026-02-27")
            add("Majority", "2026-03-28")
            add("Majority", "2027-01-01")
            add("Tied", "2025-02-27")
            add("Tied", "2026-02-27")
            add("Month", "2024-02-27")
            add("Month", "2024-02-28")
            add("Year", "2023-02-27")
            add("Year", "2023-03-27")
            assertEquals(first.releaseDate, dao.getMediaFile(first.id!!)!!.releaseDate)
            val catalog = KtorCoreMediaCatalogService(ds, ds)
            val albums = catalog.albums(folder.id!!.toLong(), 100, 0).associateBy { it.name }
            assertEquals("2026-02-27", albums.getValue("Majority").releaseDate.toString())
            assertNull(albums.getValue("Tied").releaseDate)
            assertEquals("2024-02", albums.getValue("Month").releaseDate.toString())
            assertEquals("2023", albums.getValue("Year").releaseDate.toString())
            assertEquals(1, catalog.albums(null, 1, 0, fromYear = 2026, toYear = 2026).size)
            assertEquals(1L, catalog.countAlbums(null, fromYear = 2026, toYear = 2026))
            assertEquals(catalog.album(folder.id!!.toLong(), "Majority")!!.releaseDate,
                albums.getValue("Majority").releaseDate)
            val auth = LocalAuthService(ds)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val token = auth.login("admin", "a strong password for tests")!!.token
            testApplication {
                application { configureApiRouting(authService = auth, mediaCatalogService = catalog) }
                val mapper = jacksonObjectMapper()
                val response = client.get("/api/v1/tracks/${first.id}") { bearerAuth(token) }
                assertEquals(200, response.status.value, response.bodyAsText())
                assertEquals("2026-02-27", mapper.readTree(response.bodyAsText())["releaseDate"].asText())
                val albumId = kordinatorCatalogId(folder.id!!.toLong(), "Majority")
                val detail = client.get("/api/v1/albums/$albumId") { bearerAuth(token) }
                assertEquals(200, detail.status.value, detail.bodyAsText())
                assertEquals("2026-02-27", mapper.readTree(detail.bodyAsText())["releaseDate"].asText())
            }
        }
    }

    @Test fun rescanRereadsUnchangedFilesAndPreservesIdsAndUserMetadata() {
        val music = Files.createDirectory(home.resolve("music"))
        val file = music.resolve("track.mp3")
        Files.copy(Path.of("src/test/resources/MEDIAS/piano.mp3"), file)
        val audio = AudioFileIO.read(file.toFile()) as MP3File
        audio.setID3v2Tag(ID3v24Tag())
        audio.tagOrCreateAndSetDefault.setField(FieldKey.YEAR, "2026-02-27")
        audio.commit()
        KtorRuntimeFixture(home.resolve("runtime"), music, "rescan_dates_${home.fileName}").use { runtime ->
            val folder = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
            val services = createRealMediaServices(runtime)
            val dao = runtime.runtime.createPersistenceGraph().daos.mediaFileDao
            val first = services.mediaFileService.getMediaFile(Paths.get("track.mp3"), folder, false)!!
            first.playCount = 7
            first.comment = "Keep me"
            dao.createOrUpdateMediaFile(first) {}
            StandaloneDatabase.migrate(runtime.coreDataSource)
            val catalog = KtorCoreMediaCatalogService(runtime.coreDataSource, runtime.coreDataSource)
            val accountId = LocalAuthService(runtime.coreDataSource).createInitialAdmin("admin", "a strong password for tests")
            val playlists = su.nnedition.kord.service.StandalonePlaylistService(runtime.coreDataSource, catalog)
            val playlist = playlists.create(accountId, "Keep this playlist", listOf(first.id!!.toLong()))
            val favorites = su.nnedition.kord.service.StandaloneFavoriteService(runtime.coreDataSource, catalog)
            assertTrue(favorites.set(accountId, first.id!!.toLong(), true))

            runtime.coreDataSource.connection.use { c ->
                c.prepareStatement("UPDATE media_file SET version = 4, release_date = NULL WHERE id = ?").use {
                    it.setInt(1, first.id!!); it.executeUpdate()
                }
            }
            val scans = su.nnedition.kord.service.library.scan.StandaloneLibraryScanService(
                runtime.coreDataSource, runtime.mediaFolderService, services.mediaFileService, services.mediaScannerService,
            )
            val job = scans.enqueue(folder.id!!.toLong())
            scans.run(job.id)
            assertEquals("COMPLETED", scans.latestScan(folder.id!!.toLong())!!.state)
            val after = dao.getMediaFile(first.id!!)!!
            assertEquals(listOf(first.id!!.toLong()), playlists.get(accountId, playlist.id)!!.tracks.map { it.id })
            assertEquals(listOf(first.id!!.toLong()), favorites.list(accountId, 100, 0).map { it.id })

            assertEquals(first.id, after.id)
            assertEquals(ReleaseDate(2026, 2, 27), after.releaseDate)
            assertEquals(7, after.playCount)
            assertEquals("Keep me", after.comment)
        }
    }
    @Test fun httpTagEditsPreserveUnspecifiedDatesAndCanReplaceOrRemoveThem() {
        val music = Files.createDirectory(home.resolve("music"))
        val source = Files.walk(Path.of("src/test/resources/MEDIAS")).use { paths ->
            paths.filter { it.toString().endsWith(".flac") }.findFirst().orElseThrow()
        }
        val file = Files.copy(source, music.resolve("track.flac"))
        val audio = AudioFileIO.read(file.toFile())
        audio.tag.setField(FieldKey.YEAR, "2026.02.27")
        audio.commit()
        KtorRuntimeFixture(home.resolve("runtime"), music, "edit_dates_${home.fileName}").use { runtime ->
            val ds = runtime.coreDataSource
            StandaloneDatabase.migrate(ds)
            val auth = LocalAuthService(ds)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val token = auth.login("admin", "a strong password for tests")!!.token
            val services = createRealMediaServices(runtime)
            val folder = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
            val track = services.mediaFileService.getMediaFile(Paths.get("track.flac"), folder, false)!!
            val tags = su.nnedition.kord.service.TagEditService(
                runtime.runtime.createMetaDataParserFactory(), services.mediaFileService, runtime.mediaFolderService,
                services.mediaFileService.securityService,
            )
            testApplication {
                application {
                    configureApiRouting(authService = auth, mediaFileService = services.mediaFileService,
                        mediaFolderService = runtime.mediaFolderService, tagEditService = tags)
                }
                for ((body, expected) in listOf(
                    """{"title":"A new title"}""" to "2026.02.27",
                    """{"releaseDate":"2024.02"}""" to "2024-02",
                    """{"releaseDate":null}""" to "",
                )) {
                    val response = client.put("/api/v1/tracks/${track.id}/tags") {
                        bearerAuth(token)
                        setBody(body)
                    }
                    assertEquals(200, response.status.value, response.bodyAsText())
                    assertEquals(expected, AudioFileIO.read(file.toFile()).tag.getFirst(FieldKey.YEAR))
                }
                val fromDb = runtime.runtime.createPersistenceGraph().daos.mediaFileDao.getMediaFile(track.id!!)!!
                assertNull(fromDb.releaseDate)
                assertNull(fromDb.year)
                assertEquals("A new title", fromDb.title)
                assertEquals(track.artist, fromDb.artist)
            }
        }
    }

    @Test fun upgradingAnExistingSchemaBackfillsOnlyTheKnownYear() {
        val music = Files.createDirectory(home.resolve("music"))
        KtorRuntimeFixture(home.resolve("runtime"), music, "upgrade_dates_${home.fileName}").use { runtime ->
            val folder = runtime.mediaFolderService.getAllMusicFolders(true, true).single()
            mockCoreCatalog(runtime, folder, "track.mp3", "Existing", 1)
            val dao = runtime.runtime.createPersistenceGraph().daos.mediaFileDao
            val original = dao.getMediaFile("track.mp3", folder.id!!)!!
            runtime.coreDataSource.connection.use { c ->
                c.createStatement().use { sql ->
                    sql.executeUpdate("ALTER TABLE media_file DROP COLUMN release_date")
                    sql.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID = 'media-file-release-date-1'")
                    sql.executeUpdate("UPDATE media_file SET year = 2026, version = 4")
                }
            }
            su.nnedition.kord.runtime.RuntimeLiquibaseMigrator.update(runtime.runtime.configuration, runtime.coreDataSource)
            val updated = dao.getMediaFile(original.id!!)!!
            assertEquals(original.id, updated.id)
            assertEquals(ReleaseDate(2026), updated.releaseDate)
            assertEquals(4, updated.getVersion())
        }
    }

}

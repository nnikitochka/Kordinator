package org.airsonic.player.ktor

import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandalonePlaylistService
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorFileDatabaseCopyTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun copiedCoreAndStandaloneDatabasesKeepUsersSessionsCatalogAndRelations() {
        val sourceHome = Files.createDirectory(temp.resolve("source-home"))
        val copiedHome = Files.createDirectory(temp.resolve("copied-home"))
        val musicRoot = Files.createDirectory(sourceHome.resolve("music"))
        val audio = requireNotNull(javaClass.getResourceAsStream("/MEDIAS/piano.mp3")).use { it.readBytes() }
        Files.write(musicRoot.resolve("piano.mp3"), audio)

        val previousHome = System.getProperty("airsonic.home")
        System.setProperty("airsonic.home", sourceHome.toString())
        val source = openRuntime(sourceHome, musicRoot)
        var userId = -1L
        var libraryId = -1L
        var playlistId = -1L
        var trackId = -1L
        var userToken = ""
        var sourceClosed = false
        var sourceDatabase: StandaloneDatabase? = null
        try {
            val database = StandaloneDatabase.connect(source.runtime.configuration)
            sourceDatabase = database
            database.migrate()
            val auth = LocalAuthService(database.dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            userId = auth.createUser("listener", "listener password for tests")
            userToken = auth.login("listener", "listener password for tests")!!.token

            val mediaServices = createRealMediaServices(source.runtime, source.mediaFolderService)
            val folder = source.mediaFolderService.getAllMusicFolders(true, true).single()
            libraryId = folder.id!!.toLong()
            assertTrue(auth.setAllowedLibraries(userId, setOf(libraryId)))

            val scanService = StandaloneLibraryScanService(
                database.dataSource,
                source.mediaFolderService,
                mediaServices.mediaFileService,
                mediaServices.mediaScannerService,
            )
            val scan = scanService.enqueue(libraryId)
            scanService.run(scan.id)
            assertEquals("COMPLETED", scanService.latestScan(libraryId)?.state)

            val catalog = KtorCoreMediaCatalogService(source.coreDataSource, database.dataSource)
            val track = catalog.tracks(libraryId, null, 10, 0, setOf(libraryId)).single()
            trackId = track.id
            val playlistService = StandalonePlaylistService(database.dataSource, catalog)
            playlistId = playlistService.create(userId, "Saved", listOf(trackId), setOf(libraryId)).id
            val favorites = StandaloneFavoriteService(database.dataSource, catalog)
            assertTrue(favorites.set(userId, trackId, true, setOf(libraryId)))

            database.close()
            sourceDatabase = null
            source.close()
            sourceClosed = true
            copyDirectory(sourceHome.resolve("db"), copiedHome.resolve("db"))
        } finally {
            sourceDatabase?.close()
            if (!sourceClosed) source.close()
        }

        System.setProperty("airsonic.home", copiedHome.toString())
        val restored = openRuntime(copiedHome, musicRoot)
        try {
            val database = StandaloneDatabase.connect(restored.runtime.configuration)
            database.migrate()
            database.migrate()
            try {
                val auth = LocalAuthService(database.dataSource)
                val principal = assertNotNull(auth.authenticate(userToken))
                assertEquals(userId, principal.id)
                assertEquals("listener", principal.username)
                assertEquals(setOf(libraryId), principal.allowedLibraryIds)

                val catalog = KtorCoreMediaCatalogService(restored.coreDataSource, database.dataSource)
                val track = assertNotNull(catalog.track(trackId, setOf(libraryId)))
                assertEquals("piano.mp3", track.relativePath)
                assertEquals(1, StandalonePlaylistService(database.dataSource, catalog)
                    .get(userId, playlistId, setOf(libraryId))?.tracks?.size)
                assertEquals(1, StandaloneFavoriteService(database.dataSource, catalog)
                    .count(userId, setOf(libraryId)))

                val scanJobs = database.dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT COUNT(*) FROM library_scan_job WHERE library_id = ? AND state = 'COMPLETED'")
                        .use { statement ->
                            statement.setLong(1, libraryId)
                            statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                        }
                }
                assertEquals(1, scanJobs)
            } finally {
                database.close()
            }
        } finally {
            restored.close()
            if (previousHome == null) System.clearProperty("airsonic.home")
            else System.setProperty("airsonic.home", previousHome)
        }
    }

    private fun openRuntime(home: Path, musicRoot: Path): KtorRuntimeFixture = KtorRuntimeFixture(
        home,
        musicRoot,
        "unused_${home.fileName}",
        "jdbc:hsqldb:file:${home.resolve("db/airsonic")};hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false",
    )

    private fun copyDirectory(source: Path, target: Path) {
        Files.createDirectories(target)
        Files.walk(source).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                val relative = source.relativize(file)
                val destination = target.resolve(relative)
                Files.createDirectories(destination.parent)
                Files.copy(file, destination)
            }
        }
    }
}

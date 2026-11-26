package su.nnedition.kord.ktor

import su.nnedition.kord.runtime.RuntimeCompositionRoot
import su.nnedition.kord.runtime.RuntimeConfiguration
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.media.MediaScannerService
import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.security.LegacyCredentialEncoderRegistry
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import org.mockito.Mockito

internal class KtorRuntimeFixture(
    home: Path,
    defaultMusicFolder: Path,
    databaseName: String,
    coreDatabaseUrl: String? = null,
) : AutoCloseable {
    val runtime = RuntimeCompositionRoot(
        RuntimeConfiguration(
            mapOf(
                "kordinator.home" to home.toString(),
                "spring.datasource.url" to (coreDatabaseUrl
                    ?: "jdbc:hsqldb:mem:$databaseName;hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false"),
                "spring.datasource.driver-class-name" to "org.hsqldb.jdbc.JDBCDriver",
                "spring.datasource.username" to "sa",
                "spring.datasource.password" to "",
                "spring.liquibase.parameters.defaultMusicFolder" to defaultMusicFolder.toString(),
                "spring.liquibase.parameters.mysqlVarcharLimit" to "255",
            ),
        ),
    )

    val mediaFolderService: MediaFolderService
    val coreDataSource: javax.sql.DataSource

    init {
        runtime.createPersistenceGraph()
        coreDataSource = runtime.createDataSource()
        mediaFolderService = runtime.createMediaFolderService()
        if (mediaFolderService.getAllMusicFolders(true, true, true).isEmpty()) {
            mediaFolderService.createMusicFolder(
                MusicFolder(
                    null,
                    defaultMusicFolder,
                    "Music",
                    MusicFolder.Type.MEDIA,
                    true,
                    Instant.now(),
                ),
            )
        }
    }

    override fun close() {
        runtime.lifecycleRegistry.shutdown()
    }
}

internal data class CoreCatalogFixture(
    val mediaFileService: MediaFileService,
    val mediaScannerService: MediaScannerService,
)

internal data class RealMediaServices(
    val mediaFileService: MediaFileService,
    val mediaScannerService: MediaScannerService,
)

internal fun createRealMediaServices(runtime: KtorRuntimeFixture): RealMediaServices {
    return createRealMediaServices(runtime.runtime, runtime.mediaFolderService)
}

internal fun createRealMediaServices(
    root: RuntimeCompositionRoot,
    folders: MediaFolderService = root.createMediaFolderService(),
): RealMediaServices {
    val daos = root.createPersistenceGraph().daos
    val settings = root.createSettingsService()
    val security = root.createSecurityService(
        daos.userDao,
        settings,
        folders,
        LegacyCredentialEncoderRegistry(settings),
    )
    val coverArt: CoverArtService = root.createCoverArtService(daos.coverArtDao)
    val mediaFiles = root.createMediaFileService(
        security,
        settings,
        daos.mediaFileDao,
        daos.albumDao,
        root.createJaudiotaggerParser(),
        root.createMetaDataParserFactory(),
        coverArt,
    )
    val index = root.createIndexManager()
    val playlists: PlaylistService = root.createPlaylistService(security, settings, mediaFiles, coverArt)
    val scanner = root.createMediaScannerService(playlists, settings, index, mediaFiles, folders, coverArt)
    return RealMediaServices(mediaFiles, scanner)
}

internal fun mockCoreCatalog(
    runtime: KtorRuntimeFixture,
    folder: MusicFolder,
    relativePath: String,
    title: String,
    sizeBytes: Long,
    scanner: MediaScannerService = Mockito.mock(MediaScannerService::class.java),
    mediaFiles: MediaFileService = Mockito.mock(MediaFileService::class.java),
    artist: String? = null,
    album: String? = null,
    albumArtist: String? = null,
    genre: String? = null,
): CoreCatalogFixture {
    val folderId = requireNotNull(folder.id)
    val root = MediaFile().apply {
        path = ""
        this.folderId = folderId
        mediaType = MediaFile.MediaType.DIRECTORY
        this.title = "Music"
        present = true
    }
    val audio = MediaFile().apply {
        path = relativePath
        this.folderId = folderId
        mediaType = MediaFile.MediaType.MUSIC
        this.title = title
        this.artist = artist
        albumName = album
        this.albumArtist = albumArtist
        this.genre = genre
        fileSize = sizeBytes
        changed = Instant.now()
        present = true
    }
    val now = Instant.now()
    val persistedFile = MediaFile().apply {
        path = relativePath
        this.folderId = folderId
        mediaType = MediaFile.MediaType.MUSIC
        format = relativePath.substringAfterLast('.', "")
        this.title = title
        this.artist = artist
        albumName = album
        this.albumArtist = albumArtist
        this.genre = genre
        parentPath = ""
        fileSize = sizeBytes
        duration = 0.0
        created = now
        changed = now
        lastScanned = now
        childrenLastUpdated = now
        present = true
    }
    runtime.runtime.createPersistenceGraph().daos.mediaFileDao.createOrUpdateMediaFile(persistedFile) { }
    Mockito.`when`(mediaFiles.getMediaFile(Paths.get(""), folder, true)).thenReturn(root)
    Mockito.`when`(mediaFiles.getChildrenOf(root, true, true, true, true)).thenReturn(listOf(audio))
    return CoreCatalogFixture(mediaFiles, scanner)
}

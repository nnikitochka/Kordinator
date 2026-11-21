package org.airsonic.player.ktor

import org.airsonic.player.runtime.RuntimeCompositionRoot
import org.airsonic.player.runtime.RuntimeConfiguration
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.security.LegacyCredentialEncoderRegistry
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import org.mockito.Mockito

internal class KtorRuntimeFixture(home: Path, defaultMusicFolder: Path, databaseName: String) : AutoCloseable {
    val runtime = RuntimeCompositionRoot(
        RuntimeConfiguration(
            mapOf(
                "airsonic.home" to home.toString(),
                "spring.datasource.url" to "jdbc:hsqldb:mem:$databaseName;hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false",
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
    val root = runtime.runtime
    val daos = root.createPersistenceGraph().daos
    val settings = root.createSettingsService()
    val folders = runtime.mediaFolderService
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
        root.createLocaleService(settings),
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
): CoreCatalogFixture {
    val folderId = requireNotNull(folder.id)
    val root = MediaFile().apply {
        path = ""
        this.folderId = folderId
        mediaType = MediaFile.MediaType.DIRECTORY
        this.title = "Music"
        setPresent(true)
    }
    val audio = MediaFile().apply {
        path = relativePath
        this.folderId = folderId
        mediaType = MediaFile.MediaType.MUSIC
        this.title = title
        fileSize = sizeBytes
        changed = Instant.now()
        setPresent(true)
    }
    val now = Instant.now()
    val persistedFile = MediaFile().apply {
        path = relativePath
        this.folderId = folderId
        mediaType = MediaFile.MediaType.MUSIC
        format = relativePath.substringAfterLast('.', "")
        this.title = title
        parentPath = ""
        fileSize = sizeBytes
        duration = 0.0
        created = now
        changed = now
        lastScanned = now
        childrenLastUpdated = now
        setPresent(true)
    }
    runtime.runtime.createPersistenceGraph().daos.mediaFileDao.createOrUpdateMediaFile(persistedFile) { }
    Mockito.`when`(mediaFiles.getMediaFile(Paths.get(""), folder, true)).thenReturn(root)
    Mockito.`when`(mediaFiles.getChildrenOf(root, true, true, true, true)).thenReturn(listOf(audio))
    return CoreCatalogFixture(mediaFiles, scanner)
}

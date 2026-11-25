package su.nnedition.kord.runtime

import su.nnedition.kord.security.CredentialEncoderRegistry
import org.mockito.Mockito.mock

/** Builds the media services used in scanner and search tests. */
class MediaRuntimeFixture(database: RuntimeDatabaseRule) {
    private val runtime = database.runtime
    val daos: DaoGraph = database.persistence.daos
    private val settings = runtime.createSettingsService()
    val mediaFolders = runtime.createMediaFolderService()
    private val security = runtime.createSecurityService(
        daos.userDao,
        settings,
        mediaFolders,
        mock(CredentialEncoderRegistry::class.java),
    )
    private val coverArt = runtime.createCoverArtService(daos.coverArtDao)
    val mediaFiles = runtime.createMediaFileService(
        security,
        settings,
        daos.mediaFileDao,
        daos.albumDao,
        runtime.createJaudiotaggerParser(),
        runtime.createMetaDataParserFactory(),
        coverArt,
    )
    val index = runtime.createIndexManager()
    private val playlists = runtime.createPlaylistService(security, settings, mediaFiles, coverArt)
    val scanner = runtime.createMediaScannerService(playlists, settings, index, mediaFiles, mediaFolders, coverArt)
    val search = runtime.createSearchService(mediaFiles)
}

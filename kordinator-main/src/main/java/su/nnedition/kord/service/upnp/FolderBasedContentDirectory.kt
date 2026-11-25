package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.CoverArtScheme
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MediaLibraryStatistics
import su.nnedition.kord.domain.Playlist
import su.nnedition.kord.domain.User
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.search.IndexManager
import su.nnedition.kord.util.Util
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.DIDLObject
import org.fourthline.cling.support.model.PersonWithRole
import org.fourthline.cling.support.model.SortCriterion
import org.fourthline.cling.support.model.WriteStatus
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.MusicAlbum
import org.fourthline.cling.support.model.container.PlaylistContainer
import org.fourthline.cling.support.model.container.StorageFolder
import org.fourthline.cling.support.model.item.Item
import org.fourthline.cling.support.model.item.MusicTrack
import org.slf4j.LoggerFactory
import java.net.URI

/** Legacy folder-based DLNA browsing used by the embedded UPnP server. */
class FolderBasedContentDirectory(
    mediaFileService: MediaFileService?,
    private val mediaFolderService: MediaFolderService?,
    playlistService: PlaylistService?,
    private val indexManager: IndexManager?,
) : CustomContentDirectory() {
    private var mediaFileService: MediaFileService? = mediaFileService
    private var playlistService: PlaylistService? = playlistService

    @Throws(ContentDirectoryException::class)
    override fun browse(
        objectId: String?,
        browseFlag: BrowseFlag?,
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderby: Array<out SortCriterion>?,
    ): BrowseResult {
        LOG.info(
            "UPnP request - objectId: $objectId, browseFlag: $browseFlag, filter: $filter, " +
                "firstResult: $firstResult, maxResults: $maxResults",
        )
        val resultLimit = if (maxResults == 0L) Int.MAX_VALUE.toLong() else maxResults

        try {
            if (CONTAINER_ID_ROOT == objectId) {
                return if (browseFlag == BrowseFlag.METADATA) browseRootMetadata() else browseRoot(firstResult, resultLimit)
            }
            if (CONTAINER_ID_PLAYLIST_ROOT == objectId) {
                return if (browseFlag == BrowseFlag.METADATA) browsePlaylistRootMetadata() else browsePlaylistRoot(firstResult, resultLimit)
            }
            if (objectId!!.startsWith(CONTAINER_ID_PLAYLIST_PREFIX)) {
                val playlistId = objectId.replace(CONTAINER_ID_PLAYLIST_PREFIX, "").toInt()
                val playlist = playlistService!!.getPlaylist(playlistId)!!
                return if (browseFlag == BrowseFlag.METADATA) {
                    browsePlaylistMetadata(playlist)
                } else {
                    browsePlaylist(playlist, firstResult, resultLimit)
                }
            }

            val mediaFileId = objectId.replace(CONTAINER_ID_FOLDER_PREFIX, "").toInt()
            val mediaFile = mediaFileService!!.getMediaFile(mediaFileId)!!
            return if (browseFlag == BrowseFlag.METADATA) {
                browseMediaFileMetadata(mediaFile)
            } else {
                browseMediaFile(mediaFile, firstResult, resultLimit)
            }
        } catch (failure: Throwable) {
            LOG.error("UPnP error: $failure", failure)
            throw ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, failure.toString())
        }
    }

    @Throws(Exception::class)
    private fun browseRootMetadata(): BrowseResult {
        val root = StorageFolder().apply {
            id = CustomContentDirectory.CONTAINER_ID_ROOT
            parentID = "-1"
        }
        val statistics: MediaLibraryStatistics? = indexManager!!.getStatistics()
        root.storageUsed = statistics?.getTotalLengthInBytes() ?: 0L
        root.title = "Kordinator Media"
        root.isRestricted = true
        root.isSearchable = false
        root.writeStatus = WriteStatus.NOT_WRITABLE
        root.childCount = mediaFolderService!!.getAllMusicFolders().size + 1

        val didl = DIDLContent().apply { addContainer(root) }
        return createBrowseResult(didl, 1, 1)
    }

    @Throws(Exception::class)
    private fun browsePlaylistRootMetadata(): BrowseResult {
        val didl = DIDLContent().apply { addContainer(createPlaylistRootContainer()) }
        return createBrowseResult(didl, 1, 1)
    }

    @Throws(Exception::class)
    private fun browsePlaylistRoot(firstResult: Long, maxResults: Long): BrowseResult {
        val didl = DIDLContent()
        val allPlaylists = playlistService!!.allPlaylists
        val selectedPlaylists = Util.subList(allPlaylists, firstResult, maxResults)
        selectedPlaylists.forEach { didl.addContainer(createPlaylistContainer(it)) }
        return createBrowseResult(didl, selectedPlaylists.size, allPlaylists.size)
    }

    @Throws(Exception::class)
    private fun browsePlaylistMetadata(playlist: Playlist): BrowseResult {
        val didl = DIDLContent().apply { addContainer(createPlaylistContainer(playlist)) }
        return createBrowseResult(didl, 1, 1)
    }

    @Throws(Exception::class)
    private fun browsePlaylist(playlist: Playlist, firstResult: Long, maxResults: Long): BrowseResult {
        val allChildren = playlistService!!.getFilesInPlaylist(playlist.id)
        val selectedChildren = Util.subList(allChildren, firstResult, maxResults)
        val didl = DIDLContent()
        selectedChildren.forEach { addContainerOrItem(didl, it) }
        return createBrowseResult(didl, selectedChildren.size, allChildren.size)
    }

    @Throws(Exception::class)
    private fun browseRoot(firstResult: Long, maxResults: Long): BrowseResult {
        val didl = DIDLContent()
        val allFolders = mediaFolderService!!.getAllMusicFolders()
        val selectedFolders = Util.subList(allFolders, firstResult, maxResults)
        selectedFolders.forEach { folder ->
            val mediaFile = mediaFileService!!.getMediaFile("", folder)!!
            addContainerOrItem(didl, mediaFile)
        }
        if (maxResults > selectedFolders.size) {
            didl.addContainer(createPlaylistRootContainer())
        }
        return createBrowseResult(didl, didl.count.toInt(), allFolders.size + 1)
    }

    @Throws(Exception::class)
    private fun browseMediaFileMetadata(mediaFile: MediaFile): BrowseResult {
        val didl = DIDLContent().apply { addContainer(createContainer(mediaFile)) }
        return createBrowseResult(didl, 1, 1)
    }

    @Throws(Exception::class)
    private fun browseMediaFile(mediaFile: MediaFile, firstResult: Long, maxResults: Long): BrowseResult {
        val allChildren = mediaFileService!!.getChildrenOf(mediaFile, true, true, true)
        val selectedChildren = Util.subList(allChildren, firstResult, maxResults)
        val didl = DIDLContent()
        selectedChildren.forEach { addContainerOrItem(didl, it) }
        return createBrowseResult(didl, selectedChildren.size, allChildren.size)
    }

    private fun addContainerOrItem(didl: DIDLContent, mediaFile: MediaFile) {
        if (mediaFile.isFile) didl.addItem(createItem(mediaFile)) else didl.addContainer(createContainer(mediaFile))
    }

    private fun createItem(song: MediaFile): Item {
        val parent = mediaFileService!!.getParentOf(song)!!
        val item = MusicTrack()
        item.id = java.lang.String.valueOf(song.id)
        item.parentID = java.lang.String.valueOf(parent.id)
        item.title = song.title
        item.album = song.albumName
        song.artist?.let { item.artists = arrayOf(PersonWithRole(it)) }
        song.year?.let { item.date = "$it-01-01" }
        item.originalTrackNumber = song.trackNumber
        song.genre?.let { item.genres = arrayOf(it) }
        item.resources = listOf(createResourceForSong(song))
        item.description = song.comment
        item.addProperty(DIDLObject.Property.UPNP.ALBUM_ART_URI(getAlbumArtUrl(parent)))
        return item
    }

    private fun createContainer(mediaFile: MediaFile): Container {
        val container = if (mediaFile.isAlbum) createAlbumContainer(mediaFile) else MusicAlbum()
        container.id = CONTAINER_ID_FOLDER_PREFIX + mediaFile.id
        container.title = mediaFile.name
        container.childCount = mediaFileService!!.getChildrenOf(mediaFile, true, true, false).size
        container.parentID = CustomContentDirectory.CONTAINER_ID_ROOT
        if (!mediaFileService!!.isRoot(mediaFile)) {
            mediaFileService!!.getParentOf(mediaFile)?.let { container.parentID = it.id.toString() }
        }
        return container
    }

    private fun createAlbumContainer(album: MediaFile): Container {
        val container = MusicAlbum()
        container.albumArtURIs = arrayOf(getAlbumArtUrl(album))
        album.artist?.let { container.artists = arrayOf(PersonWithRole(it)) }
        container.description = album.comment
        return container
    }

    private fun createPlaylistRootContainer(): Container {
        val playlists = playlistService!!.allPlaylists
        return StorageFolder().apply {
            id = CONTAINER_ID_PLAYLIST_ROOT
            title = "Playlists"
            childCount = playlists.size
            parentID = CustomContentDirectory.CONTAINER_ID_ROOT
        }
    }

    private fun createPlaylistContainer(playlist: Playlist): Container = PlaylistContainer().apply {
        id = CONTAINER_ID_PLAYLIST_PREFIX + playlist.id
        parentID = CONTAINER_ID_PLAYLIST_ROOT
        title = playlist.name
        description = playlist.comment
        childCount = playlistService!!.getFilesInPlaylist(playlist.id).size
    }

    private fun getAlbumArtUrl(album: MediaFile): URI {
        val path = "ext/coverArt.view?id=${album.id}&size=${CoverArtScheme.LARGE.size}"
        val signedPath = jwtSecurityService!!.addJWTToken(User.USERNAME_ANONYMOUS, path)
        return URI.create(getBaseUrl() + signedPath)
    }

    fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    fun setPlaylistService(playlistService: PlaylistService) {
        this.playlistService = playlistService
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(FolderBasedContentDirectory::class.java)
        private const val CONTAINER_ID_PLAYLIST_ROOT = "playlists"
        private const val CONTAINER_ID_PLAYLIST_PREFIX = "playlist-"
        private const val CONTAINER_ID_FOLDER_PREFIX = "folder-"
    }
}

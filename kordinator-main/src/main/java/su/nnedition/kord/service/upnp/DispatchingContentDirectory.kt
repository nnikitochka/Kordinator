package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.CoverArtScheme
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.User
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.MusicIndexService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SearchService
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLObject
import org.fourthline.cling.support.model.PersonWithRole
import org.fourthline.cling.support.model.SortCriterion
import org.fourthline.cling.support.model.item.Item
import org.fourthline.cling.support.model.item.MusicTrack
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.regex.Pattern

/** Routes DLNA browse and search requests to content processors. */
class DispatchingContentDirectory(
    mediaFileProcessor: MediaFileUpnpProcessor?,
    playlistProcessor: PlaylistUpnpProcessor?,
    albumUpnpProcessor: AlbumUpnpProcessor?,
    recentAlbumUpnpProcessor: RecentAlbumUpnpProcessor?,
    artistProcessor: ArtistUpnpProcessor?,
    genreProcessor: GenreUpnpProcessor?,
    rootProcessor: RootUpnpProcessor?,
    mediaFileService: MediaFileService?,
    mediaFolderService: MediaFolderService?,
    playlistService: PlaylistService?,
    searchService: SearchService?,
    musicIndexService: MusicIndexService?,
) : CustomContentDirectory() {
    private var playlistProcessorRef: PlaylistUpnpProcessor? = playlistProcessor
    private var mediaFileProcessorRef: MediaFileUpnpProcessor? = mediaFileProcessor
    private var albumUpnpProcessorRef: AlbumUpnpProcessor? = albumUpnpProcessor
    private var recentAlbumUpnpProcessorRef: RecentAlbumUpnpProcessor? = recentAlbumUpnpProcessor
    private var artistProcessorRef: ArtistUpnpProcessor? = artistProcessor
    private var genreProcessorRef: GenreUpnpProcessor? = genreProcessor
    private var rootProcessorRef: RootUpnpProcessor? = rootProcessor
    private var mediaFileServiceRef: MediaFileService? = mediaFileService
    private var mediaFolderServiceRef: MediaFolderService? = mediaFolderService
    private var playlistServiceRef: PlaylistService? = playlistService
    private var musicIndexServiceRef: MusicIndexService? = musicIndexService
    private var searchServiceRef: SearchService? = searchService

    @Throws(ContentDirectoryException::class)
    override fun browse(
        objectId: String?,
        browseFlag: BrowseFlag?,
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult {
        LOG.info(
            "UPnP request - objectId: $objectId, browseFlag: $browseFlag, filter: $filter, " +
                "firstResult: $firstResult, maxResults: $maxResults",
        )
        if (objectId == null) {
            throw ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, "objectId is null")
        }

        val resultLimit = if (maxResults == 0L) Long.MAX_VALUE else maxResults
        try {
            val splitId = Pattern.compile(SEPARATOR).split(objectId)
            val browseRoot = splitId[0]
            var itemId = if (splitId.size == 1) null else splitId[1]
            val processor = findProcessor(browseRoot) ?: run {
                itemId = browseRoot
                getMediaFileProcessor()
            }
            return if (itemId == null) {
                if (browseFlag == BrowseFlag.METADATA) {
                    processor.browseRootMetadata()
                } else {
                    processor.browseRoot(filter, firstResult, resultLimit, orderBy)
                }
            } else if (browseFlag == BrowseFlag.METADATA) {
                processor.browseObjectMetadata(itemId)
            } else {
                processor.browseObject(itemId, filter, firstResult, resultLimit, orderBy)
            }
        } catch (failure: Throwable) {
            LOG.error("UPnP error: $failure", failure)
            throw ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, failure.toString())
        }
    }

    @Throws(ContentDirectoryException::class)
    override fun search(
        containerId: String?,
        searchCriteria: String?,
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult {
        val criteria = searchCriteria!!
        val upnpClass = Pattern.compile("^.*upnp:class\\s+[\\S]+\\s+\"([\\S]*)\".*$").matcher(criteria).replaceAll("$1")
        val titleSearch = Pattern.compile("^.*dc:title\\s+[\\S]+\\s+\"([\\S]*)\".*$").matcher(criteria).replaceAll("$1")
        val result = when {
            upnpClass.equals("object.container.person.musicArtist", ignoreCase = true) ->
                getArtistProcessor().searchByName(titleSearch, firstResult, maxResults, orderBy)
            upnpClass.equals("object.item.audioItem", ignoreCase = true) ->
                getMediaFileProcessor().searchByName(titleSearch, firstResult, maxResults, orderBy)
            upnpClass.equals("object.container.album.musicAlbum", ignoreCase = true) ->
                getAlbumProcessor().searchByName(titleSearch, firstResult, maxResults, orderBy)
            else -> null
        }
        return result ?: super.search(containerId, criteria, filter, firstResult, maxResults, orderBy)
    }

    private fun findProcessor(type: String): UpnpContentProcessor<*, *>? = when (type) {
        CONTAINER_ID_ROOT -> getRootProcessor()
        CONTAINER_ID_PLAYLIST_PREFIX -> getPlaylistProcessor()
        CONTAINER_ID_FOLDER_PREFIX -> getMediaFileProcessor()
        CONTAINER_ID_ALBUM_PREFIX -> getAlbumProcessor()
        CONTAINER_ID_RECENT_PREFIX -> getRecentAlbumProcessor()
        CONTAINER_ID_ARTIST_PREFIX -> getArtistProcessor()
        CONTAINER_ID_GENRE_PREFIX -> getGenreProcessor()
        else -> null
    }

    fun createItem(song: MediaFile): Item {
        val parent = getMediaFileService()!!.getParentOf(song)!!
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
        item.addProperty(DIDLObject.Property.UPNP.ALBUM_ART_URI(getAlbumArtUrl(parent.id!!)))
        return item
    }

    fun getAlbumArtUrl(id: Int): URI {
        val path = "ext/coverArt.view?id=$id&size=${CoverArtScheme.LARGE.size}"
        val signedPath = getJwtSecurityService()!!.addJWTToken(User.USERNAME_ANONYMOUS, path)
        return URI.create(getBaseUrl() + signedPath)
    }

    fun getPlaylistProcessor(): PlaylistUpnpProcessor = playlistProcessorRef!!
    fun setPlaylistProcessor(processor: PlaylistUpnpProcessor?) { playlistProcessorRef = processor }
    fun getMediaFileProcessor(): MediaFileUpnpProcessor = mediaFileProcessorRef!!
    fun setMediaFileProcessor(processor: MediaFileUpnpProcessor?) { mediaFileProcessorRef = processor }
    fun getAlbumProcessor(): AlbumUpnpProcessor = albumUpnpProcessorRef!!
    fun setAlbumProcessor(processor: AlbumUpnpProcessor?) { albumUpnpProcessorRef = processor }
    fun getRecentAlbumProcessor(): RecentAlbumUpnpProcessor = recentAlbumUpnpProcessorRef!!
    fun setRecentAlbumProcessor(processor: RecentAlbumUpnpProcessor?) { recentAlbumUpnpProcessorRef = processor }
    fun getArtistProcessor(): ArtistUpnpProcessor = artistProcessorRef!!
    fun setArtistProcessor(processor: ArtistUpnpProcessor?) { artistProcessorRef = processor }
    fun getGenreProcessor(): GenreUpnpProcessor = genreProcessorRef!!
    fun setGenreProcessor(processor: GenreUpnpProcessor?) { genreProcessorRef = processor }
    fun getRootProcessor(): RootUpnpProcessor = rootProcessorRef!!
    fun setRootProcessor(processor: RootUpnpProcessor?) { rootProcessorRef = processor }
    fun getMediaFileService(): MediaFileService = mediaFileServiceRef!!
    fun setMediaFileService(service: MediaFileService?) { mediaFileServiceRef = service }
    fun getMediaFolderService(): MediaFolderService = mediaFolderServiceRef!!
    fun setMediaFolderService(service: MediaFolderService?) { mediaFolderServiceRef = service }
    fun getSettingsService(): su.nnedition.kord.service.SettingsService = settingsService!!
    fun getPlaylistService(): PlaylistService = playlistServiceRef!!
    fun setPlaylistService(service: PlaylistService?) { playlistServiceRef = service }
    fun getJwtSecurityService(): JWTSecurityService = jwtSecurityService!!
    fun getMusicIndexService(): MusicIndexService = musicIndexServiceRef!!
    fun setMusicIndexService(service: MusicIndexService?) { musicIndexServiceRef = service }
    fun getSearchService(): SearchService = searchServiceRef!!
    fun setSearchService(service: SearchService?) { searchServiceRef = service }

    companion object {
        private val LOG = LoggerFactory.getLogger(DispatchingContentDirectory::class.java)
        const val CONTAINER_ID_ROOT = "0"
        const val CONTAINER_ID_PLAYLIST_PREFIX = "playlist"
        const val CONTAINER_ID_FOLDER_PREFIX = "folder"
        const val CONTAINER_ID_ALBUM_PREFIX = "album"
        const val CONTAINER_ID_ARTIST_PREFIX = "artist"
        const val CONTAINER_ID_ARTISTALBUM_PREFIX = "artistalbum"
        const val CONTAINER_ID_GENRE_PREFIX = "genre"
        const val CONTAINER_ID_RECENT_PREFIX = "recent"
        const val SEPARATOR = "-"
    }
}

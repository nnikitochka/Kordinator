package org.airsonic.player.service.upnp

import com.google.common.primitives.Ints
import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.User
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.PersonWithRole
import org.fourthline.cling.support.model.SortCriterion
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.MusicAlbum
import java.net.URI

/** DLNA album containers and tracks. */
open class AlbumUpnpProcessor : UpnpContentProcessor<Album, MediaFile>() {
    private var albumDao: AlbumDao? = null

    init {
        rootId = DispatchingContentDirectory.CONTAINER_ID_ALBUM_PREFIX
        rootTitle = "Albums"
    }

    override fun browseRoot(filter: String?, firstResult: Long, maxResults: Long, orderBy: Array<out SortCriterion>?): BrowseResult {
        val didl = DIDLContent()
        val folders = getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders()
        getAlbumDao().getAlphabeticalAlbums(Ints.saturatedCast(firstResult), Ints.saturatedCast(maxResults), false, true, folders)
            .forEach { addItem(didl, it) }
        return createBrowseResult(didl, didl.count.toLong(), getAllItemsSize().toLong())
    }

    override fun createContainer(album: Album): Container = MusicAlbum().apply {
        if (album.id == -1) {
            id = rootId + DispatchingContentDirectory.SEPARATOR + album.comment
        } else {
            id = rootId + DispatchingContentDirectory.SEPARATOR + album.id
            albumArtURIs = arrayOf(getAlbumArtURI(album.id))
            description = album.comment
        }
        parentID = rootId
        title = album.name
        album.artist?.let { artists = getAlbumArtists(it) }
    }

    override fun getAllItems(): List<Album> = getAlbumDao().getAlphabeticalAlbums(
        0, Int.MAX_VALUE, false, true, getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders(),
    )

    override fun getItemById(id: String): Album? = if (id.startsWith(ALL_BY_ARTIST) || id.equals(ALL_RECENT, true)) {
        Album().apply { this.id = -1; comment = id }
    } else {
        getAlbumDao().getAlbum(id.toInt())
    }

    override fun getChildren(album: Album): List<MediaFile> {
        val files = getMediaFileDao().getSongsForAlbum(album.artist!!, album.name!!).toMutableList()
        if (album.id == -1) {
            val comment = album.comment!!
            val albums = when {
                comment.startsWith(ALL_BY_ARTIST) -> {
                    val artistProcessor = getDispatcher().getArtistProcessor()
                    artistProcessor.getChildren(artistProcessor.getItemById(comment.replace(Regex("${ALL_BY_ARTIST}_"), ""))!!)
                }
                comment.equals(ALL_RECENT, true) -> getDispatcher().getRecentAlbumProcessor().getAllItems()
                else -> emptyList()
            }
            albums.filter { it.id != -1 }.forEach { files.addAll(getMediaFileDao().getSongsForAlbum(it.artist!!, it.name!!)) }
        } else {
            return getMediaFileDao().getSongsForAlbum(album.artist!!, album.name!!)
        }
        return files
    }

    override fun getAllItemsSize(): Int = getAlbumDao().getAlbumCount(getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders())

    override fun addChild(didl: DIDLContent, child: MediaFile) {
        didl.addItem(getDispatcher().getMediaFileProcessor().createItem(child))
    }

    fun getAlbumArtURI(albumId: Int): URI {
        val path = "ext/coverArt.view?id=$albumId&size=${CoverArtScheme.LARGE.size}"
        val signedPath = getDispatcher().getJwtSecurityService().addJWTToken(User.USERNAME_ANONYMOUS, path)
        return URI.create(getDispatcher().getBaseUrl() + signedPath)
    }

    fun getAlbumArtists(artist: String): Array<PersonWithRole> = arrayOf(PersonWithRole(artist))
    fun getAlbumDao(): AlbumDao = albumDao!!
    fun setAlbumDao(albumDao: AlbumDao) { this.albumDao = albumDao }
    fun getMediaFileDao(): MediaFileDao = getDispatcher().getMediaFileProcessor().getMediaFileDao()

    companion object {
        const val ALL_BY_ARTIST = "allByArtist"
        const val ALL_RECENT = "allRecent"
    }
}

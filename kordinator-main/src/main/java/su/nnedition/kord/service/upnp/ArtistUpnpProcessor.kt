package su.nnedition.kord.service.upnp

import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.Artist
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.MusicArtist

/** DLNA artist containers and their albums. */
class ArtistUpnpProcessor : UpnpContentProcessor<Artist, Album>() {
    private var artistDao: ArtistDao? = null

    init {
        rootId = DispatchingContentDirectory.CONTAINER_ID_ARTIST_PREFIX
        rootTitle = "Artists"
    }

    override fun createContainer(artist: Artist): Container = MusicArtist().apply {
        id = rootId + DispatchingContentDirectory.SEPARATOR + artist.id
        parentID = rootId
        title = artist.name
        childCount = artist.albumCount
    }

    override fun getAllItems(): List<Artist> {
        val folders = getDispatcher().getMediaFolderService().getAllMusicFolders()
        return getArtistDao().getAlphabetialArtists(0, Int.MAX_VALUE, folders).sortedWith { first, second ->
            first.name!!.replace(Regex("\\W"), "").compareTo(second.name!!.replace(Regex("\\W"), ""), ignoreCase = true)
        }
    }

    override fun getItemById(id: String): Artist? = getArtistDao().getArtist(id.toInt())

    override fun getChildren(artist: Artist): List<Album> {
        val folders = getDispatcher().getMediaFolderService().getAllMusicFolders()
        val albums = getAlbumProcessor().getAlbumDao().getAlbumsForArtist(artist.name!!, folders).toMutableList()
        if (albums.size > 1) {
            albums.add(0, Album().apply {
                name = "- All Albums -"
                id = -1
                comment = AlbumUpnpProcessor.ALL_BY_ARTIST + "_" + artist.id
            })
        }
        return albums
    }

    override fun addChild(didl: DIDLContent, album: Album) {
        didl.addContainer(getAlbumProcessor().createContainer(album))
    }

    fun getArtistDao(): ArtistDao = artistDao!!
    fun setArtistDao(artistDao: ArtistDao) { this.artistDao = artistDao }
    fun getAlbumProcessor(): AlbumUpnpProcessor = getDispatcher().getAlbumProcessor()
}

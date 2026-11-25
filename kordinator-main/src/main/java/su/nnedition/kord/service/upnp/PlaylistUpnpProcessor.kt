package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.Playlist
import su.nnedition.kord.service.PlaylistService
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.PlaylistContainer

/** DLNA playlist containers and their tracks. */
class PlaylistUpnpProcessor : UpnpContentProcessor<Playlist, MediaFile>() {
    private var playlistService: PlaylistService? = null

    init {
        rootId = DispatchingContentDirectory.CONTAINER_ID_PLAYLIST_PREFIX
        rootTitle = "Playlists"
    }

    override fun createContainer(item: Playlist): Container = PlaylistContainer().apply {
        id = getRootId() + DispatchingContentDirectory.SEPARATOR + item.id
        parentID = getRootId()
        title = item.name
        description = item.comment
        childCount = getPlaylistService().getFilesInPlaylist(item.id).size
    }

    override fun getAllItems(): List<Playlist> = getPlaylistService().allPlaylists

    override fun getItemById(id: String): Playlist? = getDispatcher().getPlaylistService().getPlaylist(id.toInt())

    override fun getChildren(item: Playlist): List<MediaFile> = getPlaylistService().getFilesInPlaylist(item.id)

    override fun addChild(didl: DIDLContent, child: MediaFile) {
        didl.addItem(getDispatchingContentDirectory().createItem(child))
    }

    fun getPlaylistService(): PlaylistService = playlistService!!
    fun setPlaylistService(playlistService: PlaylistService) {
        this.playlistService = playlistService
    }
}

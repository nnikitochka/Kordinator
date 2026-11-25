package su.nnedition.kord.service.upnp

import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFileService
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.DIDLObject
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.MusicAlbum
import org.fourthline.cling.support.model.item.Item
import org.fourthline.cling.support.model.item.MusicTrack

/** DLNA folders, albums, and track items. */
class MediaFileUpnpProcessor : UpnpContentProcessor<MediaFile, MediaFile>() {
    private var mediaFileDao: MediaFileDao? = null

    init {
        rootId = DispatchingContentDirectory.CONTAINER_ID_FOLDER_PREFIX
        rootTitle = "Folders"
    }

    override fun browseObjectMetadata(id: String): BrowseResult {
        val item = getItemById(id)!!
        val didl = DIDLContent()
        addChild(didl, item)
        return createBrowseResult(didl, 1, 1)
    }

    override fun createContainer(item: MediaFile): Container {
        val container = MusicAlbum()
        if (item.isAlbum) {
            container.albumArtURIs = arrayOf(getDispatcher().getAlbumProcessor().getAlbumArtURI(item.id!!))
            if (item.artist != null) {
                container.artists = getDispatcher().getAlbumProcessor().getAlbumArtists(item.artist!!)
            }
            container.description = item.comment
        }
        container.id = DispatchingContentDirectory.CONTAINER_ID_FOLDER_PREFIX +
            DispatchingContentDirectory.SEPARATOR + item.id
        container.title = item.name
        container.childCount = getChildren(item).size

        if (!getMediaFileService().isRoot(item)) {
            val parent = getMediaFileService().getParentOf(item)
            if (parent != null) {
                container.parentID = parent.id.toString()
            }
        } else {
            container.parentID = DispatchingContentDirectory.CONTAINER_ID_FOLDER_PREFIX
        }
        return container
    }

    override fun getAllItems(): List<MediaFile> {
        val allFolders: List<MusicFolder> = getDispatcher().getMediaFolderService().getAllMusicFolders()
        return if (allFolders.size == 1) {
            getChildren(getMediaFileService().getMediaFile("", allFolders[0])!!)
        } else {
            allFolders.map { folder -> getMediaFileService().getMediaFile("", folder)!! }
        }
    }

    override fun getItemById(id: String): MediaFile? = getMediaFileService().getMediaFile(id.toInt())

    override fun getChildren(item: MediaFile): List<MediaFile> =
        getMediaFileService().getChildrenOf(item, true, true, true).toMutableList().apply {
            sortWith { first, second ->
                first.path!!.replace(Regex("\\W"), "").compareTo(second.path!!.replace(Regex("\\W"), ""), ignoreCase = true)
            }
        }

    override fun addItem(didl: DIDLContent, item: MediaFile) {
        if (item.isFile) didl.addItem(createItem(item)) else didl.addContainer(createContainer(item))
    }

    override fun addChild(didl: DIDLContent, child: MediaFile) {
        if (child.isFile) didl.addItem(createItem(child)) else didl.addContainer(createContainer(child))
    }

    fun createItem(song: MediaFile): Item {
        val parent = getMediaFileService().getParentOf(song)!!
        val item = MusicTrack()
        item.id = java.lang.String.valueOf(song.id)
        item.parentID = java.lang.String.valueOf(parent.id)
        item.title = song.title
        item.album = song.albumName
        if (song.artist != null) {
            item.artists = getDispatcher().getAlbumProcessor().getAlbumArtists(song.artist!!)
        }
        song.year?.let { item.date = "$it-01-01" }
        item.originalTrackNumber = song.trackNumber
        song.genre?.let { item.genres = arrayOf(it) }
        item.resources = listOf(getDispatcher().createResourceForSong(song))
        item.description = song.comment
        item.addProperty(DIDLObject.Property.UPNP.ALBUM_ART_URI(getDispatcher().getAlbumProcessor().getAlbumArtURI(parent.id!!)))
        return item
    }

    fun getMediaFileService(): MediaFileService = getDispatchingContentDirectory().getMediaFileService()
    fun getMediaFileDao(): MediaFileDao = mediaFileDao!!
    fun setMediaFileDao(mediaFileDao: MediaFileDao) { this.mediaFileDao = mediaFileDao }
}

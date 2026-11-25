package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.Album
import su.nnedition.kord.util.Util
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.SortCriterion

/** Most recently added albums in the DLNA catalog. */
class RecentAlbumUpnpProcessor : AlbumUpnpProcessor() {
    init {
        rootId = DispatchingContentDirectory.CONTAINER_ID_RECENT_PREFIX
        rootTitle = "RecentAlbums"
    }

    override fun browseRoot(filter: String?, firstResult: Long, maxResults: Long, orderBy: Array<out SortCriterion>?): BrowseResult {
        val didl = DIDLContent()
        val allItems = getAllItems()
        Util.subList(allItems, firstResult, maxResults).forEach { addItem(didl, it) }
        return createBrowseResult(didl, didl.count.toLong(), allItems.size.toLong())
    }

    override fun getAllItems(): List<Album> {
        val folders = getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders()
        val albums = getAlbumDao().getNewestAlbums(0, RECENT_COUNT, folders).toMutableList()
        if (albums.size > 1) {
            albums.add(0, Album().apply { name = "- All Albums -"; id = -1; comment = ALL_RECENT })
        }
        return albums
    }

    override fun getAllItemsSize(): Int =
        minOf(getAlbumDao().getAlbumCount(getDispatchingContentDirectory().getMediaFolderService().getAllMusicFolders()), RECENT_COUNT)

    companion object { private const val RECENT_COUNT = 50 }
}

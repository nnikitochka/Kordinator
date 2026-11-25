package org.airsonic.player.service.upnp

import org.airsonic.player.domain.Genre
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.util.Util
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.SortCriterion
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.GenreContainer

/** DLNA genre containers and their tracks. */
class GenreUpnpProcessor : UpnpContentProcessor<Genre, MediaFile>() {
    init {
        rootId = DispatchingContentDirectory.CONTAINER_ID_GENRE_PREFIX
        rootTitle = "Genres"
    }

    override fun browseRoot(
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult {
        val didl = DIDLContent()
        val allItems = allItems
        val selectedItems = Util.subList(allItems, firstResult, maxResults)
        selectedItems.forEachIndexed { index, item ->
            didl.addContainer(createContainer(item, (index + firstResult).toInt()))
        }
        return createBrowseResult(didl, didl.count.toLong(), allItems.size.toLong())
    }

    override fun createContainer(item: Genre): Container? = null

    fun createContainer(item: Genre, index: Int): Container = GenreContainer().apply {
        id = rootId + DispatchingContentDirectory.SEPARATOR + index
        parentID = rootId
        title = item.name
        childCount = item.getAlbumCount()
    }

    override fun getAllItems(): List<Genre> = dispatcher.getMediaFileService().getGenres(false)

    override fun getItemById(id: String): Genre? {
        val index = id.toInt()
        val allGenres = allItems
        return if (allGenres.size > index) allGenres[index] else null
    }

    override fun getChildren(item: Genre): List<MediaFile> {
        val allFolders: List<MusicFolder> = dispatcher.getMediaFolderService().getAllMusicFolders()
        return dispatcher.getMediaFileProcessor().getMediaFileDao()
            .getSongsByGenre(item.name, 0, Int.MAX_VALUE, allFolders)
    }

    override fun addChild(didl: DIDLContent, child: MediaFile) {
        didl.addItem(dispatcher.getMediaFileProcessor().createItem(child))
    }
}

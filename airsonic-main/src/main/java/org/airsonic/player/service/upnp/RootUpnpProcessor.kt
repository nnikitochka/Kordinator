package org.airsonic.player.service.upnp

import org.airsonic.player.service.search.IndexManager
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.WriteStatus
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.StorageFolder

/** Provides the six root DLNA categories. */
class RootUpnpProcessor(private val indexManager: IndexManager) : UpnpContentProcessor<Container, Container>() {
    override fun createRootContainer(): Container {
        val root = StorageFolder()
        root.id = DispatchingContentDirectory.CONTAINER_ID_ROOT
        root.parentID = "-1"
        indexManager.getStatistics()
        root.storageUsed = -1L
        root.title = "Airsonic Media"
        root.isRestricted = true
        root.isSearchable = true
        root.writeStatus = WriteStatus.NOT_WRITABLE
        root.childCount = 6
        return root
    }

    override fun createContainer(item: Container): Container = item

    override fun getAllItems(): List<Container> {
        val directory = getDispatchingContentDirectory()
        return arrayListOf(
            directory.getAlbumProcessor().createRootContainer(),
            directory.getArtistProcessor().createRootContainer(),
            directory.getMediaFileProcessor().createRootContainer(),
            directory.getGenreProcessor().createRootContainer(),
            directory.getPlaylistProcessor().createRootContainer(),
            directory.getRecentAlbumProcessor().createRootContainer(),
        )
    }

    override fun getItemById(id: String): Container = createRootContainer()
    override fun getChildren(item: Container): List<Container> = getAllItems()
    override fun addChild(didl: DIDLContent, child: Container) = Unit
}

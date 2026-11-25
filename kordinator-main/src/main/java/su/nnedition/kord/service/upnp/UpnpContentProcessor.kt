package su.nnedition.kord.service.upnp

import com.google.common.primitives.Ints
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.ParamSearchResult
import su.nnedition.kord.util.Util
import org.fourthline.cling.support.contentdirectory.DIDLParser
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.SortCriterion
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.StorageFolder
import java.lang.reflect.ParameterizedType

/** Shared browse, metadata and name-search behavior for typed DLNA processors. */
abstract class UpnpContentProcessor<T, U> {
    @JvmField
    protected var dispatchingContentDirectory: DispatchingContentDirectory? = null

    @JvmField
    protected var rootTitle: String? = null

    @JvmField
    protected var rootId: String? = null

    @Throws(Exception::class)
    open fun browseRootMetadata(): BrowseResult {
        val didl = DIDLContent()
        didl.addContainer(createRootContainer())
        return createBrowseResult(didl, 1, 1)
    }

    @Throws(Exception::class)
    open fun createRootContainer(): Container {
        val container = StorageFolder()
        container.id = getRootId()
        container.title = getRootTitle()
        container.childCount = getAllItemsSize()
        container.parentID = DispatchingContentDirectory.CONTAINER_ID_ROOT
        return container
    }

    @Throws(Exception::class)
    open fun browseRoot(
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult {
        val didl = DIDLContent()
        val allItems = getAllItems()
        // Filtering and ordering are not implemented in the original DLNA API.
        val selectedItems = Util.subList(allItems, firstResult, maxResults)
        selectedItems.forEach { addItem(didl, it) }
        return createBrowseResult(didl, didl.count.toLong(), allItems.size.toLong())
    }

    @Throws(Exception::class)
    open fun browseObjectMetadata(id: String): BrowseResult {
        val item = getItemById(id)!!
        val didl = DIDLContent()
        addItem(didl, item)
        return createBrowseResult(didl, 1, 1)
    }

    @Throws(Exception::class)
    open fun browseObject(
        id: String,
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult {
        val item = getItemById(id)!!
        val allChildren = getChildren(item)
        // Filtering and ordering are not implemented in the original DLNA API.
        val selectedChildren = Util.subList(allChildren, firstResult, maxResults)
        val didl = DIDLContent()
        selectedChildren.forEach { addChild(didl, it) }
        return createBrowseResult(didl, selectedChildren.size.toLong(), allChildren.size.toLong())
    }

    @Throws(Exception::class)
    protected open fun createBrowseResult(didl: DIDLContent, count: Long, totalMatches: Long): BrowseResult =
        BrowseResult(DIDLParser().generate(didl), count, totalMatches)

    @Suppress("UNCHECKED_CAST")
    open fun searchByName(
        name: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult? {
        val didl = DIDLContent()
        val clazz = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>
        return try {
            val directory = getDispatchingContentDirectory()!!
            val allFolders: List<MusicFolder> = directory.getMediaFolderService().getAllMusicFolders()
            val result: ParamSearchResult<T> = directory.getSearchService().searchByName(
                name,
                Ints.saturatedCast(firstResult),
                Ints.saturatedCast(maxResults),
                allFolders,
                clazz,
            )
            result.items.forEach { addItem(didl, it) }
            createBrowseResult(didl, didl.count.toLong(), result.totalHits.toLong())
        } catch (_: Exception) {
            null
        }
    }

    fun getDispatchingContentDirectory(): DispatchingContentDirectory = dispatchingContentDirectory!!
    open fun setDispatchingContentDirectory(directory: DispatchingContentDirectory?) {
        dispatchingContentDirectory = directory
    }

    fun getDispatcher(): DispatchingContentDirectory = getDispatchingContentDirectory()

    open fun addItem(didl: DIDLContent, item: T) {
        didl.addContainer(createContainer(item))
    }

    @Throws(Exception::class)
    open fun getAllItemsSize(): Int = getAllItems().size

    abstract fun createContainer(item: T): Container?

    @Throws(Exception::class)
    abstract fun getAllItems(): List<T>

    abstract fun getItemById(id: String): T?

    @Throws(Exception::class)
    abstract fun getChildren(item: T): List<U>

    abstract fun addChild(didl: DIDLContent, child: U)

    fun getRootTitle(): String? = rootTitle
    open fun setRootTitle(rootTitle: String?) { this.rootTitle = rootTitle }
    fun getRootId(): String? = rootId
    open fun setRootId(rootId: String?) { this.rootId = rootId }
}

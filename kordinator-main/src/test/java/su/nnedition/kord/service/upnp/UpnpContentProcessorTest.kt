package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.ParamSearchResult
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.SearchService
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.container.StorageFolder
import org.fourthline.cling.support.model.item.MusicTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UpnpContentProcessorTest {
    @Test
    fun browsesRootAndObjectsWithPaginationAndMetadata() {
        val processor = SampleContentProcessor()

        val root = processor.createRootContainer()
        val page = processor.browseRoot(null, 1, 1, null)
        val metadata = processor.browseObjectMetadata("one")
        val children = processor.browseObject("one", null, 1, 1, null)

        assertEquals("sample", root.id)
        assertEquals("Sample", root.title)
        assertEquals(2, root.childCount)
        assertEquals(DispatchingContentDirectory.CONTAINER_ID_ROOT, root.parentID)
        assertEquals(1L, page.countLong)
        assertEquals(2L, page.totalMatchesLong)
        assertTrue(page.result.contains("two"))
        assertTrue(metadata.result.contains("one"))
        assertEquals(1L, children.countLong)
        assertEquals(2L, children.totalMatchesLong)
        assertTrue(children.result.contains("one-2"))
    }

    @Test
    fun searchesUsingConcreteProcessorTypeAndReturnsTotalHits() {
        val folders = listOf(MusicFolder(1, java.nio.file.Path.of("/music"), "Music", MusicFolder.Type.MEDIA, true, null))
        val folderService = mock(MediaFolderService::class.java)
        `when`(folderService.getAllMusicFolders()).thenReturn(folders)
        val searchService = mock(SearchService::class.java)
        val searchResult = ParamSearchResult<String>().apply {
            items.add("matched")
            totalHits = 9
        }
        `when`(searchService.searchByName<String>("matched", 2, 5, folders, String::class.java)).thenReturn(searchResult)
        val dispatcher = DispatchingContentDirectory(
            null, null, null, null, null, null, null,
            null, folderService, null, searchService, null,
        )
        val processor = SampleContentProcessor().apply { setDispatchingContentDirectory(dispatcher) }

        val result = processor.searchByName("matched", 2, 5, null)

        assertNotNull(result)
        assertEquals(1L, result!!.countLong)
        assertEquals(9L, result.totalMatchesLong)
        assertTrue(result.result.contains("matched"))
    }

    private class SampleContentProcessor : UpnpContentProcessor<String, String>() {
        init {
            rootId = "sample"
            rootTitle = "Sample"
        }

        override fun createContainer(item: String): Container = StorageFolder().apply {
            id = item
            parentID = "sample"
            title = item
        }

        override fun getAllItems(): List<String> = listOf("one", "two")

        override fun getItemById(id: String): String? = id.takeIf { it == "one" || it == "two" }

        override fun getChildren(item: String): List<String> = listOf("$item-1", "$item-2")

        override fun addChild(didl: DIDLContent, child: String) {
            didl.addItem(MusicTrack().apply {
                id = child
                parentID = "sample"
                title = child
            })
        }
    }
}

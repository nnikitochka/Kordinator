package su.nnedition.kord.service.search

import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.ParamSearchResult
import su.nnedition.kord.domain.RandomSearchCriteria
import su.nnedition.kord.domain.SearchCriteria
import su.nnedition.kord.domain.SearchResult
import su.nnedition.kord.service.SearchService
import org.apache.commons.lang3.StringUtils.isEmpty
import org.apache.lucene.document.Document
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TopDocs
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Random
import java.util.function.BiConsumer

class SearchServiceImpl(
    private val queryFactory: QueryFactory,
    private val indexManager: IndexManager,
    private val util: SearchServiceUtilities
) : SearchService {
    // TODO Should be changed to SecureRandom?
    private val random = Random(System.currentTimeMillis())

    override fun search(
        criteria: SearchCriteria?,
        musicFolders: List<MusicFolder>?,
        indexType: IndexType?
    ): SearchResult {
        val searchCriteria = criteria!!
        val folders = musicFolders!!
        val type = indexType!!
        val result = SearchResult()
        val offset = searchCriteria.offset
        val count = searchCriteria.count
        result.offset = offset
        if (count <= 0) return result

        val searcher = indexManager.getSearcher(type) ?: return result
        try {
            val query = queryFactory.search(searchCriteria, folders, type)
            val topDocs = searcher.search(query, offset + count)
            val totalHits = util.round.apply(topDocs.totalHits.value)
            result.totalHits = totalHits
            val start = minOf(offset, totalHits)
            val end = minOf(start + count, totalHits)
            for (index in start until end) {
                util.addIfAnyMatch(result, type, searcher.doc(topDocs.scoreDocs[index].doc))
            }
        } catch (error: IOException) {
            LOG.error("Failed to execute Lucene search.", error)
        } finally {
            indexManager.release(type, searcher)
        }
        return result
    }

    @Throws(IOException::class)
    private fun <D> createRandomDocsList(
        count: Int,
        searcher: IndexSearcher,
        query: Query,
        idToListCallback: BiConsumer<MutableList<D>, Int>
    ): List<D> {
        val docs = Arrays.stream(searcher.search(query, Int.MAX_VALUE).scoreDocs)
            .map { it.doc }
            .toList()
            .toMutableList()
        val result = ArrayList<D>()
        while (docs.isNotEmpty() && result.size < count) {
            val randomPosition = random.nextInt(docs.size)
            val document: Document = searcher.doc(docs.removeAt(randomPosition))
            idToListCallback.accept(result, util.getId.apply(document))
        }
        return result
    }

    override fun getRandomSongs(criteria: RandomSearchCriteria?): List<MediaFile> {
        val searcher = indexManager.getSearcher(IndexType.SONG) ?: return Collections.emptyList()
        try {
            val query = queryFactory.getRandomSongs(criteria!!)
            return createRandomDocsList(criteria.count, searcher, query) { destination, id ->
                util.addIgnoreNull(destination, IndexType.SONG, id)
            }
        } catch (error: IOException) {
            LOG.error("Failed to search or random songs.", error)
        } finally {
            indexManager.release(IndexType.SONG, searcher)
        }
        return Collections.emptyList()
    }

    override fun getRandomAlbums(count: Int, musicFolders: List<MusicFolder>?): List<MediaFile> {
        val searcher = indexManager.getSearcher(IndexType.ALBUM) ?: return Collections.emptyList()
        val query = queryFactory.getRandomAlbums(musicFolders!!)
        try {
            return createRandomDocsList(count, searcher, query) { destination, id ->
                util.addIgnoreNull(destination, IndexType.ALBUM, id)
            }
        } catch (error: IOException) {
            LOG.error("Failed to search for random albums.", error)
        } finally {
            indexManager.release(IndexType.ALBUM, searcher)
        }
        return Collections.emptyList()
    }

    override fun getRandomAlbumsId3(count: Int, musicFolders: List<MusicFolder>?): List<Album> {
        val searcher = indexManager.getSearcher(IndexType.ALBUM_ID3) ?: return Collections.emptyList()
        val query = queryFactory.getRandomAlbumsId3(musicFolders!!)
        try {
            return createRandomDocsList(count, searcher, query) { destination, id ->
                util.addIgnoreNull(destination, IndexType.ALBUM_ID3, id)
            }
        } catch (error: IOException) {
            LOG.error("Failed to search for random albums.", error)
        } finally {
            indexManager.release(IndexType.ALBUM_ID3, searcher)
        }
        return Collections.emptyList()
    }

    override fun <T : Any?> searchByName(
        name: String?,
        offset: Int,
        count: Int,
        folderList: List<MusicFolder>?,
        clazz: Class<T>?
    ): ParamSearchResult<T> {
        val assignableClass = clazz!!
        val indexType = util.getIndexType.apply(assignableClass)
        val fieldName = util.getFieldName.apply(assignableClass)
        val result = ParamSearchResult<T>()
        result.offset = offset
        if (indexType == null || isEmpty(fieldName) || count <= 0) return result

        val searcher = indexManager.getSearcher(indexType) ?: return result
        try {
            val query = queryFactory.searchByName(fieldName!!, name!!)
            val sortFields = indexType.getFields().map { SortField(it, SortField.Type.STRING) }.toTypedArray()
            val topDocs: TopDocs = searcher.search(query, offset + count, Sort(*sortFields))
            val totalHits = util.round.apply(topDocs.totalHits.value)
            result.totalHits = totalHits
            val start = minOf(offset, totalHits)
            val end = minOf(start + count, totalHits)
            for (index in start until end) {
                val document = searcher.doc(topDocs.scoreDocs[index].doc)
                util.addIgnoreNull(result, indexType, util.getId.apply(document), assignableClass)
            }
        } catch (error: IOException) {
            LOG.error("Failed to execute Lucene search.", error)
        } finally {
            indexManager.release(indexType, searcher)
        }
        return result
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SearchServiceImpl::class.java)
    }
}

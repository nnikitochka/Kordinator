package su.nnedition.kord.service

import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.ParamSearchResult
import su.nnedition.kord.domain.RandomSearchCriteria
import su.nnedition.kord.domain.SearchCriteria
import su.nnedition.kord.domain.SearchResult
import su.nnedition.kord.service.search.IndexType

/** Performs Lucene-based searching. */
@JvmSuppressWildcards
interface SearchService {
    fun search(criteria: SearchCriteria?, musicFolders: List<MusicFolder>?, indexType: IndexType?): SearchResult

    /** Returns random songs matching the supplied criteria. */
    fun getRandomSongs(criteria: RandomSearchCriteria?): List<MediaFile>

    /** Returns random albums from the supplied folders. */
    fun getRandomAlbums(count: Int, musicFolders: List<MusicFolder>?): List<MediaFile>

    /** Returns random albums using ID3 tags. */
    fun getRandomAlbumsId3(count: Int, musicFolders: List<MusicFolder>?): List<Album>

    fun <T> searchByName(
        name: String?,
        offset: Int,
        count: Int,
        folderList: List<MusicFolder>?,
        clazz: Class<T>?
    ): ParamSearchResult<T>
}

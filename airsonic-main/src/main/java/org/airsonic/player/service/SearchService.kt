package org.airsonic.player.service

import org.airsonic.player.domain.Album
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.ParamSearchResult
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.domain.SearchCriteria
import org.airsonic.player.domain.SearchResult
import org.airsonic.player.service.search.IndexType

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

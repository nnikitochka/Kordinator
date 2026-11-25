package org.airsonic.player.runtime

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.search.AnalyzerFactory
import org.airsonic.player.service.search.DocumentFactory
import org.airsonic.player.service.search.IndexManager
import org.airsonic.player.service.search.QueryFactory
import org.airsonic.player.service.search.SearchServiceImpl
import org.airsonic.player.service.search.SearchServiceUtilities

/** Builds search indexing, query and utility components. */
object RuntimeSearchServiceFactory {
    @JvmStatic
    fun createAnalyzerFactory() = AnalyzerFactory()

    @JvmStatic
    fun createDocumentFactory() = DocumentFactory()

    @JvmStatic
    fun createIndexManager(
        analyzerFactory: AnalyzerFactory,
        documentFactory: DocumentFactory,
        mediaFileDao: MediaFileDao,
        artistDao: ArtistDao,
        albumDao: AlbumDao,
    ) = IndexManager(analyzerFactory, documentFactory, mediaFileDao, artistDao, albumDao)

    @JvmStatic
    fun createQueryFactory(analyzerFactory: AnalyzerFactory) = QueryFactory(analyzerFactory)

    @JvmStatic
    fun createSearchServiceUtilities(
        artistDao: ArtistDao,
        albumDao: AlbumDao,
        mediaFileService: MediaFileService,
    ) = SearchServiceUtilities(artistDao, albumDao, mediaFileService)

    @JvmStatic
    fun createSearchService(
        queryFactory: QueryFactory,
        indexManager: IndexManager,
        utilities: SearchServiceUtilities,
    ) = SearchServiceImpl(queryFactory, indexManager, utilities)
}

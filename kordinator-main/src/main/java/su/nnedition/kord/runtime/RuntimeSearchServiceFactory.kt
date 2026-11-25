package su.nnedition.kord.runtime

import su.nnedition.kord.dao.AlbumDao
import su.nnedition.kord.dao.ArtistDao
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.search.AnalyzerFactory
import su.nnedition.kord.service.search.DocumentFactory
import su.nnedition.kord.service.search.IndexManager
import su.nnedition.kord.service.search.QueryFactory
import su.nnedition.kord.service.search.SearchServiceImpl
import su.nnedition.kord.service.search.SearchServiceUtilities

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

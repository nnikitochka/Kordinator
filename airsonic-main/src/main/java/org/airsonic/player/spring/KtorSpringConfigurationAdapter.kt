package org.airsonic.player.spring

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean

/** Spring bootstrap adapter for the explicit Ktor bean factories. */
@Configuration
class KtorSpringConfigurationAdapter : KtorConfiguration() {
    @Bean
    fun jCacheCacheManager(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): javax.cache.CacheManager =
        runtime.createCacheManager()

    @Bean(destroyMethod = "close")
    fun metricsManager(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): org.airsonic.player.monitor.MetricsManager =
        runtime.createMetricsManager()

    @Bean
    fun serverHealthContributors(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        dataSource: javax.sql.DataSource,
    ): org.airsonic.player.health.HealthContributorRegistry = runtime.createHealthContributorRegistry(dataSource)

    @Bean
    override fun persistenceGraph(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        dataSource: javax.sql.DataSource,
    ): org.airsonic.player.runtime.PersistenceGraph = runtime.createPersistenceGraph(dataSource)

    @Bean
    override fun airsonicTransactionManager(
        persistenceGraph: org.airsonic.player.runtime.PersistenceGraph,
    ): org.airsonic.player.dao.TransactionManager = persistenceGraph.transactionManager

    @Bean
    override fun jdbcHelper(persistenceGraph: org.airsonic.player.runtime.PersistenceGraph): org.airsonic.player.dao.JdbcHelper =
        persistenceGraph.jdbcHelper

    @Bean
    override fun daoGraph(persistenceGraph: org.airsonic.player.runtime.PersistenceGraph): org.airsonic.player.runtime.DaoGraph =
        persistenceGraph.daos

    @Bean override fun albumDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.AlbumDao = graph.albumDao
    @Bean override fun artistDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.ArtistDao = graph.artistDao
    @Bean override fun avatarDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.AvatarDao = graph.avatarDao
    @Bean override fun bookmarkDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.BookmarkDao = graph.bookmarkDao
    @Bean override fun coverArtDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.CoverArtDao = graph.coverArtDao
    @Bean override fun databaseDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.DatabaseDao = graph.databaseDao
    @Bean override fun mediaFileDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.MediaFileDao = graph.mediaFileDao
    @Bean override fun musicFolderDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.MusicFolderDao = graph.musicFolderDao
    @Bean override fun playQueueDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.PlayQueueDao = graph.playQueueDao
    @Bean override fun playlistDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.PlaylistDao = graph.playlistDao
    @Bean("musicFileInfoDao") override fun ratingDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.RatingDao = graph.ratingDao
    @Bean override fun shareDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.ShareDao = graph.shareDao
    @Bean override fun transcodingDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.TranscodingDao = graph.transcodingDao
    @Bean override fun userDao(graph: org.airsonic.player.runtime.DaoGraph): org.airsonic.player.dao.UserDao = graph.userDao

    @Bean
    override fun playerDao(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): org.airsonic.player.dao.PlayerDao =
        runtime.createPlayerDao()

    @Bean
    override fun playerDaoPlayQueueFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.dao.PlayerDaoPlayQueueFactory = runtime.createPlayerDaoPlayQueueFactory()

    @Bean
    override fun analyzerFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.AnalyzerFactory = runtime.createAnalyzerFactory()

    @Bean
    override fun documentFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.DocumentFactory = runtime.createDocumentFactory()

    @Bean
    override fun queryFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.QueryFactory = runtime.createQueryFactory()

    @Bean
    override fun searchServiceUtilities(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.search.SearchServiceUtilities = runtime.createSearchServiceUtilities(mediaFileService)

    @Bean
    override fun indexManager(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.search.IndexManager = runtime.createIndexManager()

    @Bean
    override fun searchServiceImpl(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        mediaFileService: org.airsonic.player.service.MediaFileService,
    ): org.airsonic.player.service.search.SearchServiceImpl = runtime.createSearchService(mediaFileService)

    @Bean
    override fun defaultMetaDataParser(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.DefaultMetaDataParser = runtime.createDefaultMetaDataParser()

    @Bean
    override fun ffmpegParser(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.FFmpegParser = runtime.createFFmpegParser()

    @Bean
    override fun jaudiotaggerParser(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.JaudiotaggerParser = runtime.createJaudiotaggerParser()

    @Bean
    override fun metaDataParserFactory(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
    ): org.airsonic.player.service.metadata.MetaDataParserFactory = runtime.createMetaDataParserFactory()
}

package org.airsonic.player.runtime

import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.media.MediaScannerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.search.AnalyzerFactory
import org.airsonic.player.service.search.DocumentFactory
import org.airsonic.player.service.search.IndexManager
import org.airsonic.player.service.search.QueryFactory
import org.airsonic.player.service.search.SearchServiceImpl
import org.airsonic.player.service.search.SearchServiceUtilities

/** Lazily assembles search components under the shared runtime lock. */
internal class RuntimeSearchComposition(private val context: RuntimeCompositionContext) : RuntimeSearchComponents {
    private var mediaScannerService: MediaScannerService? = null
    private var analyzerFactory: AnalyzerFactory? = null
    private var documentFactory: DocumentFactory? = null
    private var indexManager: IndexManager? = null
    private var queryFactory: QueryFactory? = null
    private var searchServiceUtilities: SearchServiceUtilities? = null
    private var searchService: SearchServiceImpl? = null

    override fun createMediaScannerService(
        playlistService: PlaylistService,
        settingsService: SettingsService,
        indexManager: IndexManager,
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        coverArtService: CoverArtService,
    ): MediaScannerService = synchronized(context.lock) {
        if (mediaScannerService == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before MediaScannerService")
            }
            val scannerParallelism = context.configuration.getIntProperty("MediaScannerParallelism",
                    Runtime.getRuntime().availableProcessors() + 1)
            val daos = context.persistenceGraph!!.daos
            mediaScannerService = RuntimeScannerFactory.createMediaScannerService(
                    playlistService, settingsService, indexManager, mediaFileService, mediaFolderService,
                    coverArtService, daos.mediaFileDao, daos.artistDao, daos.albumDao,
                    context.root.createTaskSchedulingService(), scannerParallelism)
            mediaScannerService!!.init()
        }
        return@synchronized mediaScannerService!!
    }

    override fun createAnalyzerFactory(): AnalyzerFactory = synchronized(context.lock) {
        if (analyzerFactory == null) {
            analyzerFactory = RuntimeSearchServiceFactory.createAnalyzerFactory()
        }
        return@synchronized analyzerFactory!!
    }

    override fun createDocumentFactory(): DocumentFactory = synchronized(context.lock) {
        if (documentFactory == null) {
            documentFactory = RuntimeSearchServiceFactory.createDocumentFactory()
        }
        return@synchronized documentFactory!!
    }

    override fun createIndexManager(): IndexManager = synchronized(context.lock) {
        if (indexManager == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before IndexManager")
            }
            val daos = context.persistenceGraph!!.daos
            indexManager = RuntimeSearchServiceFactory.createIndexManager(
                    createAnalyzerFactory(), createDocumentFactory(), daos.mediaFileDao,
                    daos.artistDao, daos.albumDao)
        }
        return@synchronized indexManager!!
    }

    override fun createQueryFactory(): QueryFactory = synchronized(context.lock) {
        if (queryFactory == null) {
            queryFactory = RuntimeSearchServiceFactory.createQueryFactory(createAnalyzerFactory())
        }
        return@synchronized queryFactory!!
    }

    override fun createSearchServiceUtilities(
        mediaFileService: MediaFileService,
    ): SearchServiceUtilities = synchronized(context.lock) {
        if (searchServiceUtilities == null) {
            if (context.persistenceGraph == null) {
                throw IllegalStateException("Persistence graph must be created before SearchServiceUtilities")
            }
            val daos = context.persistenceGraph!!.daos
            searchServiceUtilities = RuntimeSearchServiceFactory.createSearchServiceUtilities(
                    daos.artistDao, daos.albumDao, mediaFileService)
        }
        return@synchronized searchServiceUtilities!!
    }

    override fun createSearchService(
        mediaFileService: MediaFileService,
    ): SearchServiceImpl = synchronized(context.lock) {
        if (searchService == null) {
            searchService = RuntimeSearchServiceFactory.createSearchService(
                    createQueryFactory(), createIndexManager(), createSearchServiceUtilities(mediaFileService))
        }
        return@synchronized searchService!!
    }
}

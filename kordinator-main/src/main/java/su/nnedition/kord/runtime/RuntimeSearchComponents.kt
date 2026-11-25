package su.nnedition.kord.runtime

import su.nnedition.kord.service.CoverArtService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.media.MediaScannerService
import su.nnedition.kord.service.PlaylistService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.search.AnalyzerFactory
import su.nnedition.kord.service.search.DocumentFactory
import su.nnedition.kord.service.search.IndexManager
import su.nnedition.kord.service.search.QueryFactory
import su.nnedition.kord.service.search.SearchServiceImpl
import su.nnedition.kord.service.search.SearchServiceUtilities

/** Creation boundary for the search components owned by one runtime. */
interface RuntimeSearchComponents {
    fun createMediaScannerService(
        playlistService: PlaylistService,
        settingsService: SettingsService,
        indexManager: IndexManager,
        mediaFileService: MediaFileService,
        mediaFolderService: MediaFolderService,
        coverArtService: CoverArtService,
    ): MediaScannerService

    fun createAnalyzerFactory(): AnalyzerFactory

    fun createDocumentFactory(): DocumentFactory

    fun createIndexManager(): IndexManager

    fun createQueryFactory(): QueryFactory

    fun createSearchServiceUtilities(
        mediaFileService: MediaFileService,
    ): SearchServiceUtilities

    fun createSearchService(
        mediaFileService: MediaFileService,
    ): SearchServiceImpl
}

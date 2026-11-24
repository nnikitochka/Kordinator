package org.airsonic.player.runtime

import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaScannerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.search.AnalyzerFactory
import org.airsonic.player.service.search.DocumentFactory
import org.airsonic.player.service.search.IndexManager
import org.airsonic.player.service.search.QueryFactory
import org.airsonic.player.service.search.SearchServiceImpl
import org.airsonic.player.service.search.SearchServiceUtilities

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

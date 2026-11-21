package org.airsonic.player.runtime

import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlayQueueDao
import org.airsonic.player.dao.PlayerDao
import org.airsonic.player.dao.TranscodingDao
import org.airsonic.player.service.AudioScrobblerService
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.JukeboxLegacySubsonicService
import org.airsonic.player.service.JukeboxService
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.PlayQueueService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TranscodingService
import java.util.function.Supplier

/** Creates the playback and transcoding services used by the runtime graph. */
object RuntimePlaybackServiceFactory {
    @JvmStatic
    fun createTranscodingService(
        transcodingDao: TranscodingDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
        playerServiceSupplier: Supplier<PlayerService>,
    ): TranscodingService = TranscodingService(
        transcodingDao, settingsService, mediaFolderService, playerServiceSupplier,
    )

    @JvmStatic
    fun createPlayerService(
        playerDao: PlayerDao,
        statusService: StatusService,
        securityService: SecurityService,
        transcodingService: TranscodingService,
    ): PlayerService = PlayerService(playerDao, statusService, securityService, transcodingService)

    @JvmStatic
    fun createLegacyJukeboxService(
        transcodingService: TranscodingService,
        audioScrobblerService: AudioScrobblerService,
        statusService: StatusService,
        settingsService: SettingsService,
        securityService: SecurityService,
        mediaFileService: MediaFileService,
    ): JukeboxLegacySubsonicService = JukeboxLegacySubsonicService(
        transcodingService, audioScrobblerService, statusService, settingsService, securityService, mediaFileService,
    )

    @JvmStatic
    fun createJukeboxService(legacyService: JukeboxLegacySubsonicService): JukeboxService =
        JukeboxService(legacyService)

    @JvmStatic
    fun createPlayQueueService(
        searchService: SearchService,
        jukeboxService: JukeboxService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        lastFmService: LastFmService,
        ratingService: RatingService,
        playlistService: PlaylistService,
        mediaFileDao: MediaFileDao,
        mediaFolderService: MediaFolderService,
        playQueueDao: PlayQueueDao,
        jwtSecurityService: JWTSecurityService,
    ): PlayQueueService = PlayQueueService(
        searchService, jukeboxService, settingsService, mediaFileService, lastFmService,
        ratingService, playlistService, mediaFileDao, mediaFolderService, playQueueDao, jwtSecurityService,
    )
}

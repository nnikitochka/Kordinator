package org.airsonic.player.runtime

import org.airsonic.player.dao.PlayerDao
import org.airsonic.player.dao.PlayerDaoPlayQueueFactory
import org.airsonic.player.dao.TranscodingDao
import org.airsonic.player.service.AudioScrobblerService
import org.airsonic.player.service.JukeboxLegacySubsonicService
import org.airsonic.player.service.JukeboxService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.media.MediaFolderService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.TranscodingService

/** Creation boundary for the playback components owned by one runtime. */
interface RuntimePlaybackComponents {
    @Throws(Exception::class)
    fun createPlayerDao(): PlayerDao

    fun createPlayerDaoPlayQueueFactory(): PlayerDaoPlayQueueFactory

    fun createJukeboxLegacySubsonicService(
        transcodingService: TranscodingService,
        audioScrobblerService: AudioScrobblerService,
        statusService: StatusService,
        settingsService: SettingsService,
        securityService: SecurityService,
        mediaFileService: MediaFileService,
    ): JukeboxLegacySubsonicService

    fun createJukeboxService(
        legacyJukebox: JukeboxLegacySubsonicService,
    ): JukeboxService

    fun createTranscodingService(
        transcodingDao: TranscodingDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
    ): TranscodingService

    fun createTranscodingService(
        transcodingDao: TranscodingDao,
        settingsService: SettingsService,
        mediaFolderService: MediaFolderService,
        playerServiceSupplier: java.util.function.Supplier<PlayerService>,
    ): TranscodingService

    fun createPlayerService(
        playerDao: PlayerDao,
        statusService: StatusService,
        securityService: SecurityService,
        transcodingService: TranscodingService,
    ): PlayerService
}

package su.nnedition.kord.runtime

import su.nnedition.kord.dao.PlayerDao
import su.nnedition.kord.dao.PlayerDaoPlayQueueFactory
import su.nnedition.kord.dao.TranscodingDao
import su.nnedition.kord.service.AudioScrobblerService
import su.nnedition.kord.service.JukeboxLegacySubsonicService
import su.nnedition.kord.service.JukeboxService
import su.nnedition.kord.service.media.MediaFileService
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.PlayerService
import su.nnedition.kord.service.SecurityService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.StatusService
import su.nnedition.kord.service.TranscodingService

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

package org.airsonic.player.runtime

import org.airsonic.player.dao.PlayerDao
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.PathWatcherService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.VersionService

/** Owns the application graph; component groups share one lock and lifecycle registry. */
class RuntimeCompositionRoot private constructor(
    private val context: RuntimeCompositionContext,
) : RuntimeInfrastructureComponents by context.infrastructure,
    RuntimeSecurityComponents by context.security,
    RuntimeSearchComponents by context.search,
    RuntimeSettingsComponents by context.settings,
    RuntimeUpnpComponents by context.upnp,
    RuntimeMediaComponents by context.media,
    RuntimePlaylistComponents by context.playlist,
    RuntimePlaybackComponents by context.playback,
    RuntimeApplicationComponents by context.application {
    val configuration: RuntimeConfiguration get() = context.configuration
    val lifecycleRegistry: LifecycleRegistry get() = context.lifecycleRegistry

    init {
        context.root = this
    }

    @Throws(Exception::class)
    override fun createPathWatcherService(): PathWatcherService = context.infrastructure.createPathWatcherService()

    @Throws(Exception::class)
    override fun createPlayerDao(): PlayerDao = context.playback.createPlayerDao()

    @Throws(java.io.IOException::class)
    override fun createVersionService(): VersionService = context.application.createVersionService()

    @Throws(Exception::class)
    override fun createPlaylistService(
        securityService: SecurityService,
        settingsService: SettingsService,
        mediaFileService: MediaFileService,
        coverArtService: CoverArtService,
    ): PlaylistService = context.playlist.createPlaylistService(
        securityService, settingsService, mediaFileService, coverArtService,
    )

    constructor() : this(RuntimeConfiguration(RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER))

    constructor(healthStatusOrder: String) : this(RuntimeConfiguration(healthStatusOrder))

    constructor(configuration: RuntimeConfiguration) : this(configuration, LifecycleRegistry())

    constructor(configuration: RuntimeConfiguration?, lifecycleRegistry: LifecycleRegistry?) : this(
        RuntimeCompositionContext(
            java.util.Objects.requireNonNull(configuration, "configuration")!!,
            java.util.Objects.requireNonNull(lifecycleRegistry, "lifecycleRegistry")!!,
        ),
    )
}

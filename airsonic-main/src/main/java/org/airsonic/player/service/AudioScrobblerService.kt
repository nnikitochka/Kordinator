package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.domain.UserCredential.App
import org.airsonic.player.service.scrobbler.LastFMScrobbler
import org.airsonic.player.service.scrobbler.ListenBrainzScrobbler
import java.time.Instant
import java.util.EnumSet

/** Registers played tracks with configured audio scrobbling services. */
class AudioScrobblerService(
    var settingsService: SettingsService,
    var securityService: SecurityService,
) {
    @Synchronized
    fun register(mediaFile: MediaFile?, username: String, submission: Boolean, time: Instant?) {
        if (mediaFile == null || mediaFile.isVideo) return

        val userSettings = settingsService.getUserSettings(username)
        val enabledApps = EnumSet.noneOf(App::class.java)
        if (userSettings.lastFmEnabled) enabledApps.add(App.LASTFM)
        if (userSettings.listenBrainzEnabled) enabledApps.add(App.LISTENBRAINZ)

        val credentials = securityService.getDecodableCredsForApps(username, *enabledApps.toTypedArray())

        credentials[App.LASTFM]?.let { credential ->
            if (lastFMScrobbler == null) lastFMScrobbler = LastFMScrobbler()
            val decoded = securityService.decodeCredentials(credential)
            if (decoded != null) {
                lastFMScrobbler!!.register(mediaFile, credential.appUsername, decoded, submission, time)
            }
        }

        credentials[App.LISTENBRAINZ]?.let { credential ->
            if (listenBrainzScrobbler == null) listenBrainzScrobbler = ListenBrainzScrobbler()
            val decoded = securityService.decodeCredentials(credential)
            if (decoded != null) {
                listenBrainzScrobbler!!.register(
                    mediaFile,
                    userSettings.listenBrainzUrl,
                    decoded,
                    submission,
                    time,
                )
            }
        }
    }

    private var lastFMScrobbler: LastFMScrobbler? = null
    private var listenBrainzScrobbler: ListenBrainzScrobbler? = null
}

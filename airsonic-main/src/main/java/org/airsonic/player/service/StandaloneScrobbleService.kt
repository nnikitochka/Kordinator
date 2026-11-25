package org.airsonic.player.service

import org.airsonic.player.domain.PlayStatus
import org.airsonic.player.domain.Player
import org.airsonic.player.service.media.MediaFileService
import java.time.Instant
import java.util.UUID

/** Records a remote play using the existing media, now-playing and scrobbler services. */
class StandaloneScrobbleService(
    private val mediaFiles: MediaFileService,
    private val status: StatusService,
    private val scrobbler: AudioScrobblerService,
) {
    fun scrobble(username: String, trackId: Long, time: Instant?, submission: Boolean): Boolean {
        if (trackId !in 0..Int.MAX_VALUE.toLong()) return false
        val mediaFile = mediaFiles.getMediaFile(trackId.toInt()) ?: return false
        val player = Player().apply {
            name = "Kordinator"
            this.username = username
        }
        status.addRemotePlay(PlayStatus(UUID.randomUUID(), mediaFile, player, time ?: Instant.now()))
        mediaFiles.incrementPlayCount(mediaFile)
        scrobbler.register(mediaFile, username, submission, time)
        return true
    }
}

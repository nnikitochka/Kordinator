package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.domain.PlayStatus
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.domain.User
import org.airsonic.player.domain.VideoTranscodingSettings
import org.airsonic.player.service.jukebox.AudioPlayer
import org.airsonic.player.service.jukebox.AudioPlayerFactory
import org.airsonic.player.util.FileUtil
import org.slf4j.LoggerFactory
import java.io.InputStream

/** Plays music on the local audio device. */
class JukeboxLegacySubsonicService(
    private var transcodingService: TranscodingService,
    private var audioScrobblerService: AudioScrobblerService,
    private var statusService: StatusService,
    private var settingsService: SettingsService,
    private var securityService: SecurityService,
    private var mediaFileService: MediaFileService,
) : AudioPlayer.Listener {
    private var audioPlayer: AudioPlayer? = null
    private var activePlayer: Player? = null
    private var status: TransferStatus? = null
    private var playStatus: PlayStatus? = null
    private var currentPlayingFile: MediaFile? = null
    private var gainValue = AudioPlayer.DEFAULT_GAIN
    private var offset = 0

    /** Starts or pauses local playback after checking the legacy jukebox role. */
    @Synchronized
    fun updateJukebox(player: Player, offset: Int) {
        val user = securityService.getUserByName(player.username)!!
        if (!user.isJukeboxRole) {
            log.warn("{} is not authorized for jukebox playback.", user.username)
            return
        }
        updatePlayback(player, offset)
    }

    /** Entry point for calls already authenticated and library-checked by the standalone Ktor API. */
    @Synchronized
    fun updateStandaloneJukebox(player: Player, offset: Int) {
        updatePlayback(player, offset)
    }

    /** Releases the local audio device for an authenticated standalone Ktor jukebox owner. */
    @Synchronized
    fun releaseStandaloneJukebox(player: Player) {
        if (activePlayer !== player) return
        audioPlayer?.close()
        audioPlayer = null
        currentPlayingFile?.let(::onSongEnd)
        currentPlayingFile = null
        activePlayer = null
    }

    private fun updatePlayback(player: Player, offset: Int) {
        val queue = player.playQueue!!
        if (queue.status == PlayQueue.Status.PLAYING) {
            activePlayer = player
            val currentFile = synchronized(queue) { queue.getCurrentFile() }
            play(currentFile, offset)
        } else {
            audioPlayer?.pause()
        }
    }

    @Synchronized
    private fun play(file: MediaFile?, offset: Int) {
        var input: InputStream? = null
        try {
            val sameFile = file != null && file == currentPlayingFile
            val paused = audioPlayer?.getState() == AudioPlayer.State.PAUSED
            if (sameFile && paused && offset == 0) {
                audioPlayer?.play()
            } else {
                this.offset = offset
                audioPlayer?.close()
                if (audioPlayer != null) currentPlayingFile?.let(::onSongEnd)

                if (file != null) {
                    val duration = file.duration?.minus(offset) ?: 0.0
                    val parameters = TranscodingService.Parameters(
                        file,
                        VideoTranscodingSettings(0, 0, offset, duration),
                    )
                    val command = settingsService.getJukeboxCommand()
                    parameters.transcoding = Transcoding(null, "Jukebox", null, null, command, null, null, false)
                    val transcodedInput = transcodingService.getTranscodedInputStream(parameters)
                    input = transcodedInput
                    audioPlayer = AudioPlayerFactory.createAudioPlayer(transcodedInput, this)
            audioPlayer?.setGain(gainValue)
                    audioPlayer?.play()
                    onSongStart(file)
                }
            }
            currentPlayingFile = file
        } catch (failure: Exception) {
            log.error("Error in jukebox: {}", failure.toString(), failure)
            FileUtil.closeQuietly(input)
        }
    }

    @Synchronized
    override fun stateChanged(audioPlayer: AudioPlayer, state: AudioPlayer.State) {
        if (state == AudioPlayer.State.EOM) {
            val queue = player!!.playQueue!!
            queue.next()
            val currentFile = synchronized(queue) { queue.getCurrentFile() }
            play(currentFile, 0)
        }
    }

    @get:Synchronized
    val gain: Float get() = gainValue

    @get:Synchronized
    val position: Int get() = audioPlayer?.let { offset + it.getPosition() } ?: 0

    /** The player which currently uses the jukebox, or null. */
    val player: Player? get() = activePlayer

    private fun onSongStart(file: MediaFile) {
        val activePlayer = activePlayer!!
        log.info("{} starting jukebox for {} in folder {}", activePlayer.username, FileUtil.getShortPath(file.relativePath), file.folderId)
        val streamStatus = statusService.createStreamStatus(activePlayer)
        status = streamStatus
        streamStatus.mediaFile = file
        streamStatus.addBytesTransferred(file.fileSize!!)
        mediaFileService.incrementPlayCount(file)
        val activePlayStatus = PlayStatus(streamStatus.id!!, file, streamStatus.player, streamStatus.millisSinceLastUpdate)
        playStatus = activePlayStatus
        statusService.addActiveLocalPlay(activePlayStatus)
        scrobble(file, false)
    }

    private fun onSongEnd(file: MediaFile) {
        val activePlayer = activePlayer!!
        log.info("{} stopping jukebox for {} in folder {}", activePlayer.username, FileUtil.getShortPath(file.relativePath), file.folderId)
        playStatus?.let {
            statusService.removeActiveLocalPlay(it)
            playStatus = null
        }
        status?.let {
            statusService.removeStreamStatus(it)
            status = null
        }
        scrobble(file, true)
    }

    private fun scrobble(file: MediaFile, submission: Boolean) {
        val activePlayer = activePlayer!!
        if (activePlayer.clientId == null) {
            audioScrobblerService.register(file, activePlayer.username!!, submission, null)
        }
    }

    @Synchronized
    fun setGain(gain: Float) {
        this.gainValue = gain
        audioPlayer?.setGain(gain)
    }

    fun setTranscodingService(transcodingService: TranscodingService) {
        this.transcodingService = transcodingService
    }

    fun setAudioScrobblerService(audioScrobblerService: AudioScrobblerService) {
        this.audioScrobblerService = audioScrobblerService
    }

    fun setStatusService(statusService: StatusService) {
        this.statusService = statusService
    }

    fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    fun setSecurityService(securityService: SecurityService) {
        this.securityService = securityService
    }

    fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    companion object {
        private val log = LoggerFactory.getLogger(JukeboxLegacySubsonicService::class.java)
    }
}

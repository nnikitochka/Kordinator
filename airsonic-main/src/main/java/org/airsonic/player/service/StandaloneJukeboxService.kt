package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.PlayerTechnology

data class JukeboxQueueTrack(val trackId: Long, val mediaFile: MediaFile)

data class StandaloneJukeboxState(
    val status: String,
    val currentIndex: Int,
    val positionSeconds: Int,
    val gain: Float,
    val tracks: List<JukeboxQueueTrackResponse>,
)

data class JukeboxQueueTrackResponse(
    val trackId: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int?,
)

/** Owns the single server audio output and its live queue through bearer-authenticated Ktor commands. */
class StandaloneJukeboxService(
    private val folders: MediaFolderService,
    private val audio: JukeboxLegacySubsonicService,
) : AutoCloseable {
    private data class Session(
        val ownerId: Long,
        val player: Player,
        val coreIdsByMediaId: Map<Int, Long>,
        var gain: Float = DEFAULT_GAIN,
    )

    @Volatile
    private var session: Session? = null

    @Synchronized
    fun replaceQueue(ownerId: Long, username: String, tracks: List<JukeboxQueueTrack>): StandaloneJukeboxState {
        require(ownerId > 0) { "Invalid queue owner" }
        require(tracks.size <= MAX_QUEUE_SIZE) { "Queue contains too many tracks" }
        val current = session
        require(current == null || current.ownerId == ownerId) { "Server audio output is in use by another user" }
        val player = current?.player ?: Player().apply {
            name = "Kordinator server output"
            this.username = username
            technology = PlayerTechnology.JUKEBOX
            playQueue = PlayQueue { folderId -> folders.getMusicFolderById(folderId)!! }
        }
        val queue = player.playQueue!!
        if (current != null) {
            queue.status = PlayQueue.Status.STOPPED
            audio.updateStandaloneJukebox(player, 0)
        }
        queue.clear()
        queue.addFiles(false, tracks.map { it.mediaFile })
        queue.status = PlayQueue.Status.STOPPED
        val gain = current?.gain ?: DEFAULT_GAIN
        audio.setGain(gain)
        session = Session(ownerId, player, tracks.associate { requireNotNull(it.mediaFile.id) to it.trackId }, gain)
        return state(ownerId)
    }

    @Synchronized
    fun command(ownerId: Long, action: String, index: Int?, offsetSeconds: Int?, gain: Float?): StandaloneJukeboxState {
        val active = session?.takeIf { it.ownerId == ownerId } ?: throw NoSuchElementException("Jukebox queue not found")
        val queue = active.player.playQueue!!
        require(queue.size() > 0 || action in setOf("clear", "stop")) { "Jukebox queue is empty" }
        when (action) {
            "play" -> {
                val target = index ?: queue.index.coerceAtLeast(0)
                require(target in queue.files.indices) { "Track index is outside the jukebox queue" }
                val offset = offsetSeconds ?: 0
                validateOffset(queue.getFile(target), offset)
                    queue.index = target
                queue.status = PlayQueue.Status.PLAYING
                audio.updateStandaloneJukebox(active.player, offset)
            }
            "pause", "stop" -> {
                queue.status = PlayQueue.Status.STOPPED
                audio.updateStandaloneJukebox(active.player, 0)
            }
            "next" -> {
                if (queue.index + 1 >= queue.size()) {
                    queue.status = PlayQueue.Status.STOPPED
                    audio.updateStandaloneJukebox(active.player, 0)
                } else {
                    queue.index = queue.index + 1
                    queue.status = PlayQueue.Status.PLAYING
                    audio.updateStandaloneJukebox(active.player, 0)
                }
            }
            "previous" -> {
                val target = if (audio.position > 3) queue.index else (queue.index - 1).coerceAtLeast(0)
                queue.index = target
                queue.status = PlayQueue.Status.PLAYING
                audio.updateStandaloneJukebox(active.player, 0)
            }
            "seek" -> {
                val offset = offsetSeconds ?: throw IllegalArgumentException("offsetSeconds is required")
                validateOffset(queue.files.getOrNull(queue.index), offset)
                queue.status = PlayQueue.Status.PLAYING
                audio.updateStandaloneJukebox(active.player, offset)
            }
            "shuffle" -> queue.shuffle()
            "clear" -> {
                queue.status = PlayQueue.Status.STOPPED
                audio.updateStandaloneJukebox(active.player, 0)
                queue.clear()
            }
            "gain" -> {
                val value = gain ?: throw IllegalArgumentException("gain is required")
                require(value.isFinite() && value in 0.0f..1.0f) { "gain must be between 0 and 1" }
                audio.setGain(value)
                active.gain = value
            }
            else -> throw IllegalArgumentException("Unknown jukebox action")
        }
        return state(ownerId)
    }

    @Synchronized
    fun state(ownerId: Long): StandaloneJukeboxState {
        val current = session ?: return StandaloneJukeboxState("IDLE", -1, 0, 0.75f, emptyList())
        if (current.ownerId != ownerId) throw IllegalStateException("Server audio output is in use by another user")
        val queue = current.player.playQueue!!
        val index = queue.index
        val tracks = queue.files.mapNotNull { file ->
            file.id?.let(current.coreIdsByMediaId::get)?.let { id ->
                JukeboxQueueTrackResponse(id, file.title.orEmpty(), file.artist, file.albumName, file.duration?.toInt())
            }
        }
        return StandaloneJukeboxState(
            if (queue.status == PlayQueue.Status.PLAYING && index in queue.files.indices) "PLAYING" else "PAUSED",
            index,
            if (queue.status == PlayQueue.Status.PLAYING) audio.position else 0,
            current.gain,
            tracks,
        )
    }

    @Synchronized
    fun release(ownerId: Long): Boolean {
        val active = session?.takeIf { it.ownerId == ownerId } ?: return false
        val queue = active.player.playQueue!!
        queue.status = PlayQueue.Status.STOPPED
        audio.releaseStandaloneJukebox(active.player)
        queue.clear()
        session = null
        return true
    }

    @Synchronized
    override fun close() {
        session?.let { release(it.ownerId) }
    }

    private fun validateOffset(track: MediaFile?, offset: Int) {
        require(offset >= 0) { "offsetSeconds must be non-negative" }
        val duration = track?.duration?.toInt()
        require(duration == null || offset < duration) { "offsetSeconds must be before the end of the track" }
    }

    companion object {
        private const val MAX_QUEUE_SIZE = 5_000
        private const val DEFAULT_GAIN = 0.75f
    }
}

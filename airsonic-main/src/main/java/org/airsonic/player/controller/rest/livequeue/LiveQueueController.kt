package org.airsonic.player.controller.rest.livequeue

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.airsonic.player.ajax.queue.PlayQueueInfo
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.domain.Player
import org.airsonic.player.service.PlayQueueService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlaylistService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.dao.PlayQueueDao
import org.slf4j.LoggerFactory
import org.subsonic.restapi.LivePlayQueue
import org.subsonic.restapi.LiveQueueEntry
import kotlin.coroutines.cancellation.CancellationException

class LiveQueueController(private val service: PlayQueueService, private val writer: JAXBWriter, private val media: MediaFileService, private val playlists: PlaylistService, private val security: SecurityService, private val queues: PlayQueueDao) {
    data class Request(val player: Player?, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        PATHS.forEach { path -> root.post(path) { respond(call, request(call)) } }
    }

    private suspend fun respond(call: ApplicationCall, request: Request) {
        val p = request.params
        val result = try {
            val player = request.player
            if (player == null) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Player unavailable")
            else {
                fun integer(name: String): Int = requireNotNull(p[name]?.toIntOrNull()) { "Invalid $name" }
                fun optionalInt(name: String): Int? = p[name]?.let { requireNotNull(it.toIntOrNull()) }
                fun long(name: String): Long = p[name]?.let { requireNotNull(it.toLongOrNull()) } ?: 0
                fun indices(): MutableList<Int> = (p.getAll("id") ?: emptyList()).map { requireNotNull(it.toIntOrNull()) }.toMutableList()
                val action = p["action"]
                if (action in setOf("play/mediafile", "play/topsongs", "play/random", "play/similar", "add")) {
                    val ids = if (action == "add") indices() else listOf(integer("id"))
                    if (ids.isEmpty() || ids.any { id -> media.getMediaFile(id)?.let { !security.isFolderAccessAllowed(it, player.username) } ?: true }) {
                        throw SecurityException("Media access denied")
                    }
                }
                if (action == "play/saved") {
                    val ids = queues.getPlayQueue(player.username)?.mediaFileIds ?: emptyList()
                    if (ids.any { id -> media.getMediaFile(id)?.let { !security.isFolderAccessAllowed(it, player.username) } ?: false }) {
                        throw SecurityException("Media access denied")
                    }
                }
                if (action in setOf("play/playlist", "add/playlist")) {
                    val playlist = playlists.getPlaylist(integer("id"))
                    if (playlist == null || !playlists.isReadAllowed(playlist, player.username)) throw SecurityException("Playlist access denied")
                    if (playlists.getFilesInPlaylist(playlist.id).any { !security.isFolderAccessAllowed(it, player.username) }) throw SecurityException("Media access denied")
                }
                val size = player.playQueue.size()
                when (action) {
                    "skip", "up", "down" -> require(integer("index") in 0 until size)
                    "save" -> require(integer("index") in -1 until size)
                    "remove" -> { val items = indices(); require(items.distinct().size == items.size && items.all { it in 0 until size }) }
                    "rearrange" -> { val items = indices(); require(items.size == size && items.toSet() == (0 until size).toSet()) }
                    "add" -> optionalInt("index")?.let { require(it in 0..size) }
                    "jukebox/gain", "jukebox/position" -> require(player.isJukebox)
                }
                val target = PlayQueueService.PlaybackTarget.response()
                var saved: Int? = null
                var info: PlayQueueInfo? = null
                when (p["action"]) {
                    "get" -> info = service.getPlayQueueInfo(player)
                    "start" -> service.start(player)
                    "stop" -> service.stop(player)
                    "toggleStartStop" -> service.toggleStartStop(player)
                    "skip" -> service.skip(player, integer("index"), long("offset"))
                    "reloadsearch" -> info = service.reloadSearchCriteria(player, target)
                    "save" -> saved = service.savePlayQueue(player, integer("index"), long("offset"))
                    "play/saved" -> info = service.loadSavedPlayQueue(player, target)
                    "play/mediafile" -> info = service.playMediaFile(player, integer("id"), target)
                    "play/playlist" -> info = service.playPlaylist(player, integer("id"), optionalInt("index"), target)
                    "play/topsongs" -> info = service.playTopSong(player, integer("id"), optionalInt("index"), target)
                    "play/starred" -> info = service.playStarred(player, target)
                    "play/shuffle" -> info = service.playShuffle(player, p["albumListType"], long("offset").toInt(), integer("count"), p["genre"], p["decade"], target)
                    "play/random" -> info = service.playRandom(player, integer("id"), integer("count"), target)
                    "play/similar" -> info = service.playSimilar(player, integer("id"), integer("count"), target)
                    "add" -> service.add(player, indices(), optionalInt("index"), player.isWeb, true)
                    "add/playlist" -> service.addPlaylist(player, integer("id"), player.isWeb)
                    "clear" -> service.clear(player)
                    "shuffle" -> service.shuffle(player)
                    "remove" -> service.remove(player, indices())
                    "rearrange" -> service.rearrange(player, indices())
                    "up" -> service.up(player, integer("index"))
                    "down" -> service.down(player, integer("index"))
                    "toggleRepeat" -> service.toggleRepeat(player)
                    "undo" -> service.undo(player)
                    "sort" -> service.sort(player, PlayQueue.SortOrder.valueOf(requireNotNull(p["order"])))
                    "jukebox/gain" -> service.setJukeboxGain(player, requireNotNull(p["gain"]?.toFloatOrNull()))
                    "jukebox/position" -> service.setJukeboxPosition(player, integer("position"))
                    else -> throw IllegalArgumentException("Unknown action")
                }
                writer.createResponse(true).apply {
                    if (info != null || saved != null) livePlayQueue = map(info, saved)
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: SecurityException) {
            writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Queue access denied")
        }
        catch (e: Exception) {
            LOG.warn("Live queue request failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Live queue request failed")
        }
        val serialized = writer.serializeForType(p["f"] ?: "xml", p["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun map(info: PlayQueueInfo?, saved: Int?): LivePlayQueue = LivePlayQueue().apply {
        savedQueueId = saved
        if (info != null) {
            revision = info.revision
            playStatus = info.playStatus?.name
            repeatStatus = info.repeatStatus?.name
            shuffleRadioEnabled = info.isShuffleRadioEnabled
            gain = info.gain
            startPlayerAt = info.startPlayerAt
            startPlayerAtPosition = info.startPlayerAtPosition
            info.entries?.forEach { source -> entry.add(LiveQueueEntry().apply {
                id = source.id
                trackNumber = source.trackNumber
                discNumber = source.discNumber
                title = source.title
                artist = source.artist
                albumArtist = source.albumArtist
                album = source.album
                genre = source.genre
                year = source.year
                bitRate = source.bitRate
                dimensions = source.dimensions
                duration = source.duration
                format = source.format
                contentType = source.contentType
                entryType = source.entryType
                fileSize = source.fileSize
                playCount = source.playCount
                lastPlayed = source.lastPlayed?.toString()
                created = source.created?.toString()
                changed = source.changed?.toString()
                lastScanned = source.lastScanned?.toString()
                starred = source.starred
                present = source.present
                albumUrl = source.albumUrl
                streamUrl = source.streamUrl
                remoteStreamUrl = source.remoteStreamUrl
                coverArtUrl = source.coverArtUrl
                remoteCoverArtUrl = source.remoteCoverArtUrl
                accentColor = source.accentColor
            }) }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LiveQueueController::class.java)
        @JvmField val PATHS = arrayOf("/rest/livePlayQueue", "/rest/livePlayQueue.view")
    }
}

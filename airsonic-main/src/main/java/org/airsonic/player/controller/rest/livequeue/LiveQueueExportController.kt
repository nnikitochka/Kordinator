package org.airsonic.player.controller.rest.livequeue

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.Player
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.TranscodingService
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/** Exports the active player queue, independently of the saved Subsonic play queue. */
class LiveQueueExportController(
    private val transcoding: TranscodingService,
    private val jwt: JWTSecurityService,
    private val security: SecurityService,
    private val writer: JAXBWriter,
) {
    data class Request(val player: Player?, val username: String, val baseUrl: String, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        root.get(PATH) {
            val r = request(call)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val error = try {
                val content = withContext(Dispatchers.IO) {
                    val player = r.player
                    if (player == null || player.username != r.username) throw SecurityException("Player access denied")
                    val files = synchronized(player.playQueue) { player.playQueue.files.toList() }
                    if (files.any { !security.isFolderAccessAllowed(it, r.username) })
                        throw SecurityException("Music folder access denied")
                    buildString {
                        if (player.m3uBomEnabled) append('\uFEFF')
                        append("#EXTM3U\n")
                        if (player.isExternalWithPlaylist) {
                            for (file in files) {
                                // Metadata must remain on one EXTINF line, even for imported tags containing newlines.
                                val artist = file.artist.toString().replace('\r', ' ').replace('\n', ' ')
                                val title = file.title.toString().replace('\r', ' ').replace('\n', ' ')
                                append("#EXTINF:${file.duration ?: -1.0},$artist - $title\n")
                                val suffix = transcoding.getSuffix(player, file, null)
                                append(r.baseUrl).append(jwt.addJWTToken(r.username,
                                    "ext/stream?player=${player.id}&id=${file.id}&suffix=.$suffix")).append('\n')
                            }
                        } else {
                            val suffix = files.firstOrNull()?.let { "&suffix=." + transcoding.getSuffix(player, it, null) } ?: ""
                            append("#EXTINF:-1,Airsonic\n")
                            append(r.baseUrl).append(jwt.addJWTToken(r.username, "ext/stream?player=${player.id}$suffix")).append('\n')
                        }
                    }
                }
                // Commit download headers only after all checks and URL generation succeed.
                call.response.headers.append(HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "play.m3u").toString())
                call.respondText(content, ContentType.parse("audio/x-mpegurl").withParameter("charset", "UTF-8"))
                null
            } catch (e: CancellationException) { throw e
            } catch (e: SecurityException) {
                writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, e.message ?: "Queue access denied")
            } catch (e: Exception) {
                LOG.warn("Live queue export failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Live queue export failed")
            }
            if (error != null) {
                val serialized = writer.serializeForType(r.params["f"] ?: "xml", r.params["callback"], error)
                call.respondText(serialized.value, ContentType.parse(serialized.key))
            }
        }
    }

    companion object {
        const val PATH = "/rest/exportLivePlayQueue"
        private val LOG = LoggerFactory.getLogger(LiveQueueExportController::class.java)
    }
}

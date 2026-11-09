package org.airsonic.player.controller.rest.livequeue

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.PlayQueueService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException

/** Website extension for the live player queue; does not alter the saved Subsonic queue. */
class RandomQueueController(private val queues: PlayQueueService, private val folders: MediaFolderService, private val writer: JAXBWriter) {
    data class Request(val player: Player?, val username: String, val params: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        root.post(PATH) {
            val r = request(call)
            call.response.headers.append("Cache-Control", "no-store")
            val response = try {
                val player = r.player
                if (player == null || player.username != r.username) throw SecurityException("Player unavailable")
                val criteria = criteria(r.params, r.username)
                withContext(Dispatchers.IO) {
                    queues.addRandomCriteria(player, r.params.contains("addToPlaylist"), criteria, r.params.contains("autoRandom"))
                }
                writer.createResponse(true)
            } catch (e: CancellationException) { throw e
            } catch (e: SecurityException) {
                writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Queue or music folder access denied")
            } catch (e: IllegalArgumentException) {
                writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid random queue parameters")
            } catch (e: Exception) {
                LOG.warn("Random queue request failed", e)
                writer.createErrorResponse(ErrorCode.GENERIC, "Random queue request failed")
            }
            val serialized = writer.serializeForType(r.params["f"] ?: "xml", r.params["callback"], response)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }

    private fun criteria(p: Parameters, username: String): RandomSearchCriteria {
        fun integer(name: String, default: Int? = null): Int? = p[name]?.takeIf { it.isNotBlank() }?.let {
            requireNotNull(it.toIntOrNull()) { "Invalid $name" }
        } ?: default
        fun filter(name: String): String? = p[name]?.takeUnless { it.isBlank() || it.equals("any", true) }
        fun range(value: Int?, comparator: String?): Pair<Int?, Int?> {
            if (value == null) return null to null
            return when (comparator) {
                "lt" -> null to Math.decrementExact(value)
                "le" -> null to value
                "eq" -> value to value
                "ge" -> value to null
                "gt" -> Math.incrementExact(value) to null
                else -> throw IllegalArgumentException("Invalid comparison")
            }
        }
        val count = requireNotNull(integer("size", 24))
        require(count > 0) { "Invalid size" }
        val years = filter("year")?.split(Regex("\\s+"))?.map { requireNotNull(it.toIntOrNull()) { "Invalid year" } }
        require(years == null || (years.size == 2 && years[0] >= 0 && years[0] <= years[1])) { "Invalid year range" }
        val rating = integer("albumRatingValue")
        require(rating == null || rating in 0..5) { "Invalid album rating" }
        val (minRating, maxRating) = range(rating, p["albumRatingComp"])
        val playCount = integer("playCountValue")
        require(playCount == null || playCount >= 0) { "Invalid play count" }
        val (minPlays, maxPlays) = range(playCount, p["playCountComp"])
        val stars = p["songRating"] ?: "any"
        require(stars in setOf("any", "starred", "unstarred")) { "Invalid song rating" }
        val period = when (p["lastPlayedValue"] ?: "any") {
            "any" -> null
            "1day" -> ChronoUnit.DAYS.duration
            "1week" -> ChronoUnit.WEEKS.duration
            "1month" -> ChronoUnit.MONTHS.duration
            "3months" -> ChronoUnit.MONTHS.duration.multipliedBy(3)
            "6months" -> ChronoUnit.MONTHS.duration.multipliedBy(6)
            "1year" -> ChronoUnit.YEARS.duration
            else -> throw IllegalArgumentException("Invalid last played period")
        }
        var minPlayed: Instant? = null
        var maxPlayed: Instant? = null
        if (period != null) {
            val cutoff = Instant.now().minus(period)
            when (p["lastPlayedComp"]) {
                "lt" -> maxPlayed = cutoff
                "gt" -> minPlayed = cutoff
                else -> throw IllegalArgumentException("Invalid last played comparison")
            }
        }
        val folderId = requireNotNull(integer("musicFolderId", -1))
        require(folderId >= -1) { "Invalid music folder" }
        val allowed = folders.getMusicFoldersForUser(username, folderId)
        if (folderId >= 0 && allowed.isEmpty()) throw SecurityException("Music folder unavailable")
        return RandomSearchCriteria(count, filter("genre"), years?.get(0), years?.get(1), allowed,
            minPlayed, maxPlayed, minRating, maxRating, minPlays, maxPlays, stars != "unstarred", stars != "starred", filter("format"))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RandomQueueController::class.java)
        const val PATH = "/rest/createRandomPlayQueue"
    }
}

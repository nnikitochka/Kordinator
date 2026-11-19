package org.airsonic.player.controller.rest.stream

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.airsonic.player.controller.rest.RestPlayerResolver
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.ktor.KordinatorSessionService
import org.airsonic.player.ktor.bearerToken
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaStreamService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.function.BiPredicate

/** Ktor-native authenticated stream route for Kordinator clients. */
class KordinatorTrackStreamRoutes(
    private val streams: StreamRestController,
    private val sessions: KordinatorSessionService,
    private val security: SecurityService,
    private val mediaFiles: MediaFileService,
    private val players: PlayerService,
    private val playerResolver: RestPlayerResolver,
) {
    fun routes(root: Route) {
        with(root) {
            get(StreamRestController.API_TRACK_STREAM_PATH) { respond(call, false) }
            head(StreamRestController.API_TRACK_STREAM_PATH) { respond(call, true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, head: Boolean) {
        val principal = sessions.authenticate(call.bearerToken())
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null || id <= 0) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val user = security.getUserByName(principal.username)
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        if (!user.isStreamRole) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        val file = mediaFiles.getMediaFile(id)
        if (file == null || file.isDirectory ||
            (file.mediaType != MediaFile.MediaType.MUSIC && file.mediaType != MediaFile.MediaType.AUDIOBOOK)
        ) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val callerContext = security.createCallerContext(principal.username, true)
        if (!security.isFolderAccessAllowed(file, callerContext)) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val playerId = playerResolver.getOrCreatePlayerId(
            principal.username,
            call.request.queryParameters["c"],
            call.request.local.remoteHost,
            false,
        )
        val player = playerId?.let(players::getPlayerById)
        if (player == null) {
            call.respond(HttpStatusCode.InternalServerError)
            return
        }

        val parameters = call.request.queryParameters
        val rawBitRate = parameters["maxBitRate"]
        val bitRate = rawBitRate?.toIntOrNull()
        val rawOffset = parameters["offsetSeconds"]
        val offset = rawOffset?.toDoubleOrNull()
        if ((rawBitRate != null && bitRate == null) || (rawOffset != null && offset == null)) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val conditionalStatus = intArrayOf(HttpStatusCode.OK.value)
        val notModified = conditionalValidator(call, conditionalStatus)
        val request = StreamRestController.Request(
            false,
            callerContext,
            player,
            user,
            parameters["format"],
            parameters["suffix"],
            bitRate,
            id,
            null,
            offset,
            MediaStreamService.VideoOptions(
                parameters["maxBitRate"],
                parameters["timeOffset"],
                parameters["duration"],
                parameters["size"],
            ),
            call.request.headers["icy-metadata"],
            notModified,
            { conditionalStatus[0] },
        )
        streams.respond(call, request, head)
    }

    private fun conditionalValidator(call: ApplicationCall, status: IntArray): BiPredicate<String?, Long> =
        BiPredicate { tag, lastModifiedMillis ->
            val lastModified = Instant.ofEpochMilli(lastModifiedMillis).atZone(ZoneOffset.UTC).withNano(0).toInstant()
            val etag = tag?.let { if (it.startsWith('"') && it.endsWith('"')) it else "\"$it\"" }
            etag?.let { call.response.headers.append(HttpHeaders.ETag, it, safeOnly = false) }
            call.response.headers.append(HttpHeaders.LastModified, HTTP_DATE.format(lastModified), safeOnly = false)

            val ifNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]
            val matched = if (ifNoneMatch != null) {
                val normalized = etag?.removePrefix("W/")
                ifNoneMatch.split(',').any { candidate ->
                    val trimmed = candidate.trim()
                    trimmed == "*" || trimmed.removePrefix("W/") == normalized
                }
            } else {
                val ifModifiedSince = call.request.headers[HttpHeaders.IfModifiedSince]
                ifModifiedSince != null && parseHttpDate(ifModifiedSince)?.let { !lastModified.isAfter(it) } == true
            }
            if (matched) status[0] = HttpStatusCode.NotModified.value
            matched
        }

    private fun parseHttpDate(value: String): Instant? = try {
        Instant.from(HTTP_DATE.parse(value))
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        val HTTP_DATE: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
    }
}

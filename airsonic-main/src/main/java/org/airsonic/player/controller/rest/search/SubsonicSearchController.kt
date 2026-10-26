package org.airsonic.player.controller.rest.search

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.Player
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

/** Ktor routes for all three versions of the Subsonic search API. */
class SubsonicSearchController(
    private val searchService: SubsonicSearchService,
    private val jaxbWriter: JAXBWriter,
) {
    /** The temporary servlet adapter supplies the authenticated user and merged query/form parameters. */
    data class Request(val username: String, val player: Player, val parameters: Parameters)

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path ->
                val version = path.removeSuffix(".view").removePrefix("/rest/")
                get(path) { respond(call, version, request(call)) }
                post(path) { respond(call, version, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, version: String, request: Request) {
        val parameters = request.parameters
        val response = try {
            search(version, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidParameter) {
            jaxbWriter.createErrorResponse(ErrorCode.MISSING_PARAMETER, e.message)
        } catch (e: Exception) {
            LOG.warn("Error in Subsonic search API", e)
            jaxbWriter.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = jaxbWriter.serializeForType(parameters["f"] ?: "xml", parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun search(version: String, request: Request): Response {
        val (username, player, parameters) = request
        if (version == "search") {
            return searchService.search(
                username, player, parameters["any"], parameters["artist"], parameters["album"], parameters["title"],
                parameters.int("count", 20), parameters.int("offset", 0),
            )
        }
        val folderId = parameters["musicFolderId"]?.let { value ->
            value.toIntOrNull() ?: throw InvalidParameter(
                "Required int parameter 'musicFolderId' with value of '$value' is not a valid number"
            )
        }
        return when (version) {
            "search2" -> searchService.search2(
                username, player, parameters["query"], folderId,
                parameters.int("artistCount", 20), parameters.int("artistOffset", 0),
                parameters.int("albumCount", 20), parameters.int("albumOffset", 0),
                parameters.int("songCount", 20), parameters.int("songOffset", 0),
            )
            else -> searchService.search3(
                username, player, parameters["query"], folderId,
                parameters.int("artistCount", 20), parameters.int("artistOffset", 0),
                parameters.int("albumCount", 20), parameters.int("albumOffset", 0),
                parameters.int("songCount", 20), parameters.int("songOffset", 0),
            )
        }
    }

    // ServletRequestUtils previously used the default for both absent and malformed pagination values.
    private fun Parameters.int(name: String, default: Int): Int = get(name)?.toIntOrNull() ?: default

    private class InvalidParameter(message: String) : IllegalArgumentException(message)

    companion object {
        private val LOG = LoggerFactory.getLogger(SubsonicSearchController::class.java)

        @JvmField
        val PATHS = arrayOf(
            "/rest/search", "/rest/search.view",
            "/rest/search2", "/rest/search2.view",
            "/rest/search3", "/rest/search3.view",
        )
    }
}

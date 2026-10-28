package org.airsonic.player.spring

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.isHandled
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingFailureStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.servlet.jakarta.ServletApplicationEngine
import io.ktor.server.servlet.jakarta.ServletApplicationRequest
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.controller.rest.RestPlayerResolver
import org.airsonic.player.controller.rest.artists.ArtistsController
import org.airsonic.player.controller.rest.artists.ArtistsRequest
import org.airsonic.player.controller.rest.genres.GenresController
import org.airsonic.player.controller.rest.musicfolders.MusicFoldersController
import org.airsonic.player.controller.rest.ping.PingController
import org.airsonic.player.controller.rest.scan.ScanController
import org.airsonic.player.controller.rest.search.SearchController
import org.airsonic.player.controller.rest.similarsongs.SimilarSongsController
import org.airsonic.player.controller.rest.similarsongs.SimilarSongsRequest
import org.airsonic.player.controller.cover.CoverArtController
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.util.StringUtil
import org.apache.commons.lang.StringUtils
import org.springframework.security.core.context.SecurityContextHolder

class AirsonicKtorServlet(
    private val searchController: SearchController,
    private val musicFoldersController: MusicFoldersController,
    private val genresController: GenresController,
    private val artistsController: ArtistsController,
    private val pingController: PingController,
    private val scanController: ScanController,
    private val similarSongsController: SimilarSongsController,
    private val mediaFileService: MediaFileService,
    private val playerResolver: RestPlayerResolver,
    private val playerService: PlayerService,
    private val securityService: SecurityService,
) : ServletApplicationEngine() {
    @OptIn(InternalAPI::class)
    override fun init() {
        super.init()
        application.intercept(ApplicationCallPipeline.Setup) {
            val request = (call.request as ServletApplicationRequest).servletRequest
            val searchRequest = request.getAttribute(SEARCH_REQUEST) as? SearchController.Request
            if (searchRequest != null) {
                call.attributes.put(SEARCH_REQUEST_KEY, searchRequest)
            }
            val musicFoldersRequest = request.getAttribute(MUSIC_FOLDERS_REQUEST)
                as? MusicFoldersController.Request
            if (musicFoldersRequest != null) {
                call.attributes.put(MUSIC_FOLDERS_REQUEST_KEY, musicFoldersRequest)
            }
            val artistsRequest = request.getAttribute(ARTISTS_REQUEST)
                as? ArtistsRequest
            if (artistsRequest != null) {
                call.attributes.put(ARTISTS_REQUEST_KEY, artistsRequest)
            }
            val similarSongsRequest = request.getAttribute(SIMILAR_SONGS_REQUEST) as? SimilarSongsRequest
            if (similarSongsRequest != null) {
                call.attributes.put(SIMILAR_SONGS_REQUEST_KEY, similarSongsRequest)
            }
            val parameters = request.getAttribute(REST_PARAMETERS) as? Parameters
            if (parameters != null) {
                call.attributes.put(REST_PARAMETERS_KEY, parameters)
            }
        }
        application.routing {
            searchController.routes(this) { call -> call.attributes[SEARCH_REQUEST_KEY] }
            musicFoldersController.routes(this) { call -> call.attributes[MUSIC_FOLDERS_REQUEST_KEY] }
            genresController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            artistsController.routes(this) { call -> call.attributes[ARTISTS_REQUEST_KEY] }
            pingController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            scanController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            similarSongsController.routes(this) { call -> call.attributes[SIMILAR_SONGS_REQUEST_KEY] }
        }
        // The servlet engine omits BaseApplicationEngine's default fallback in Ktor 3.6.0.
        // Keep this compatibility code in the temporary servlet adapter.
        application.intercept(ApplicationCallPipeline.Fallback) {
            if (!call.isHandled) {
                call.respond(
                    call.response.status()
                        ?: call.attributes.getOrNull(RoutingFailureStatusCode)
                        ?: HttpStatusCode.NotFound
                )
            }
        }
    }

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        val path = request.servletPath
        if (path in SearchController.PATHS) {
            // Capture Spring's thread-bound authentication before Ktor dispatches to a coroutine.
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val playerRequest = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(playerRequest, response)
            request.setAttribute(SEARCH_REQUEST, SearchController.Request(username, player, parameters(request)))
        } else if (path in MusicFoldersController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(
                MUSIC_FOLDERS_REQUEST,
                MusicFoldersController.Request(username, parameters(request))
            )
        } else if (path in GenresController.PATHS) {
            // Preserve the REST player creation previously performed by wrapRequest().
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in ArtistsController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(ARTISTS_REQUEST, ArtistsRequest(username, parameters(request)))
        } else if (path in PingController.PATHS) {
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in ScanController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in SimilarSongsController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val id = mapSimilarSongsId(request.getParameter("id"))
            val parsedId = if (id == null) null else try {
                Integer.parseInt(id)
            } catch (_: NumberFormatException) {
                // Spring MVC previously sent a container error for a malformed required integer.
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerRequest = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val securityContext = SecurityContextHolder.getContext()
            request.setAttribute(SIMILAR_SONGS_REQUEST, SimilarSongsRequest(username, parameters(request), parsedId) {
                val previousContext = SecurityContextHolder.getContext()
                try {
                    SecurityContextHolder.setContext(securityContext)
                    playerService.getPlayer(playerRequest, response)
                } finally {
                    SecurityContextHolder.setContext(previousContext)
                }
            })
        }
        super.service(request, response)
    }

    private fun parameters(request: HttpServletRequest): Parameters = Parameters.build {
        // Security filters may already have consumed a POST body. Use the servlet's parsed parameters.
        request.parameterNames.asSequence().distinct().forEach { name ->
            request.getParameterValues(name)?.let { appendAll(name, it.toList()) }
        }
    }

    private fun mapSimilarSongsId(id: String?): String? {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
            id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)
        ) return id
        return try {
            mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).id.toString()
        } catch (_: Exception) {
            id
        }
    }

    private companion object {
        const val REST_PARAMETERS = "org.airsonic.player.restParameters"
        val REST_PARAMETERS_KEY = AttributeKey<Parameters>(REST_PARAMETERS)
        const val SEARCH_REQUEST = "org.airsonic.player.searchRequest"
        val SEARCH_REQUEST_KEY = AttributeKey<SearchController.Request>(SEARCH_REQUEST)
        const val MUSIC_FOLDERS_REQUEST = "org.airsonic.player.musicFoldersRequest"
        val MUSIC_FOLDERS_REQUEST_KEY = AttributeKey<MusicFoldersController.Request>(
            MUSIC_FOLDERS_REQUEST
        )
        const val ARTISTS_REQUEST = "org.airsonic.player.artistsRequest"
        val ARTISTS_REQUEST_KEY = AttributeKey<ArtistsRequest>(ARTISTS_REQUEST)
        const val SIMILAR_SONGS_REQUEST = "org.airsonic.player.similarSongsRequest"
        val SIMILAR_SONGS_REQUEST_KEY = AttributeKey<SimilarSongsRequest>(SIMILAR_SONGS_REQUEST)
    }
}

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
import org.airsonic.player.controller.rest.starred.StarredController
import org.airsonic.player.controller.rest.starred.StarredRequest
import org.airsonic.player.controller.rest.RestPlayerResolver
import org.airsonic.player.controller.rest.bookmarks.BookmarksController
import org.airsonic.player.controller.rest.bookmarks.BookmarksRequest
import org.airsonic.player.controller.rest.albums.AlbumsController
import org.airsonic.player.controller.rest.albums.AlbumsRequest
import org.airsonic.player.controller.rest.info.album.AlbumInfoController
import org.airsonic.player.controller.rest.info.album.AlbumInfoRequest
import org.airsonic.player.controller.rest.info.artist.ArtistInfoController
import org.airsonic.player.controller.rest.info.artist.ArtistInfoRequest
import org.airsonic.player.controller.rest.artists.ArtistsController
import org.airsonic.player.controller.rest.artists.ArtistsRequest
import org.airsonic.player.controller.rest.genres.GenresController
import org.airsonic.player.controller.rest.music.folder.MusicFoldersController
import org.airsonic.player.controller.rest.ping.PingController
import org.airsonic.player.controller.rest.playlists.PlaylistsController
import org.airsonic.player.controller.rest.playlists.PlaylistsRequest
import org.airsonic.player.controller.rest.scan.ScanController
import org.airsonic.player.controller.rest.shares.SharesController
import org.airsonic.player.controller.rest.shares.SharesRequest
import org.airsonic.player.controller.rest.search.SearchController
import org.airsonic.player.controller.rest.music.similar.SimilarSongsController
import org.airsonic.player.controller.rest.music.similar.SimilarSongsRequest
import org.airsonic.player.controller.cover.CoverArtController
import org.airsonic.player.i18n.LocaleResolver
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.ShareService
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
    private val artistInfoController: ArtistInfoController,
    private val albumInfoController: AlbumInfoController,
    private val playlistsController: PlaylistsController,
    private val sharesController: SharesController,
    private val albumsController: AlbumsController,
    private val bookmarksController: BookmarksController,
    private val starredController: StarredController,
    private val shareService: ShareService,
    private val mediaFileService: MediaFileService,
    private val playerResolver: RestPlayerResolver,
    private val playerService: PlayerService,
    private val securityService: SecurityService,
    private val localeResolver: LocaleResolver,
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
            val artistInfoRequest = request.getAttribute(ARTIST_INFO_REQUEST) as? ArtistInfoRequest
            if (artistInfoRequest != null) {
                call.attributes.put(ARTIST_INFO_REQUEST_KEY, artistInfoRequest)
            }
            val playlistsRequest = request.getAttribute(PLAYLISTS_REQUEST) as? PlaylistsRequest
            if (playlistsRequest != null) {
                call.attributes.put(PLAYLISTS_REQUEST_KEY, playlistsRequest)
            }
            val albumInfoRequest = request.getAttribute(ALBUM_INFO_REQUEST) as? AlbumInfoRequest
            if (albumInfoRequest != null) {
                call.attributes.put(ALBUM_INFO_REQUEST_KEY, albumInfoRequest)
            }
            val starredRequest = request.getAttribute(STARRED_REQUEST) as? StarredRequest
            if (starredRequest != null) {
                call.attributes.put(STARRED_REQUEST_KEY, starredRequest)
            }
            val bookmarksRequest = request.getAttribute(BOOKMARKS_REQUEST) as? BookmarksRequest
            if (bookmarksRequest != null) {
                call.attributes.put(BOOKMARKS_REQUEST_KEY, bookmarksRequest)
            }
            val albumsRequest = request.getAttribute(ALBUMS_REQUEST) as? AlbumsRequest
            if (albumsRequest != null) {
                call.attributes.put(ALBUMS_REQUEST_KEY, albumsRequest)
            }
            val sharesRequest = request.getAttribute(SHARES_REQUEST) as? SharesRequest
            if (sharesRequest != null) {
                call.attributes.put(SHARES_REQUEST_KEY, sharesRequest)
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
            artistInfoController.routes(this) { call -> call.attributes[ARTIST_INFO_REQUEST_KEY] }
            albumInfoController.routes(this) { call -> call.attributes[ALBUM_INFO_REQUEST_KEY] }
            playlistsController.routes(this) { call -> call.attributes[PLAYLISTS_REQUEST_KEY] }
            sharesController.routes(this) { call -> call.attributes[SHARES_REQUEST_KEY] }
            albumsController.routes(this) { call -> call.attributes[ALBUMS_REQUEST_KEY] }
            bookmarksController.routes(this) { call -> call.attributes[BOOKMARKS_REQUEST_KEY] }
            starredController.routes(this) { call -> call.attributes[STARRED_REQUEST_KEY] }
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
            val rawId = if (path.substringAfterLast('/').removeSuffix(".view") == "getArtist") mapRestId(request.getParameter("id")) else null
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ARTISTS_REQUEST, ArtistsRequest(username, parameters(request), id))
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
            val id = mapRestId(request.getParameter("id"))
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
        } else if (path in ArtistInfoController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val id = mapRestId(request.getParameter("id"))
            val parsedId = if (id == null) null else try {
                Integer.parseInt(id)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val securityContext = SecurityContextHolder.getContext()
            request.setAttribute(ARTIST_INFO_REQUEST, ArtistInfoRequest(username, parameters(request), parsedId) {
                val previousContext = SecurityContextHolder.getContext()
                try {
                    SecurityContextHolder.setContext(securityContext)
                    localeResolver.resolveLocale(request)
                } finally {
                    SecurityContextHolder.setContext(previousContext)
                }
            })
        } else if (path in AlbumInfoController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val rawId = mapRestId(request.getParameter("id"))
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ALBUM_INFO_REQUEST, AlbumInfoRequest(parameters(request), id))
        } else if (path in StarredController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val listing = operation == "getStarred" || operation == "getStarred2"
            val player = if (listing) {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            val username = if (listing) requireNotNull(securityService.getCurrentUsername(request))
                else requireNotNull(securityService.getCurrentUser(request)).username
            request.setAttribute(STARRED_REQUEST, StarredRequest(username, parameters(request), player))
        } else if (path in BookmarksController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val player = if (operation == "getBookmarks") {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val rawId = if (operation == "getBookmarks") null else mapRestId(request.getParameter("id"))
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(BOOKMARKS_REQUEST, BookmarksRequest(username, parameters(request), id, player))
        } else if (path in AlbumsController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val player = if (operation == "getAlbum" || operation == "getAlbumList") {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val rawId = if (operation == "getAlbum") mapRestId(request.getParameter("id")) else null
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ALBUMS_REQUEST, AlbumsRequest(username, parameters(request), id, player))
        } else if (path in SharesController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val player = if (operation == "getShares" || operation == "createShare") {
                val wrappedRequest = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrappedRequest, response)
            } else null
            val user = requireNotNull(securityService.getCurrentUser(request))
            val id = if (operation == "updateShare" || operation == "deleteShare")
                mapRestId(request.getParameter("id")) else null
            val context = SecurityContextHolder.getContext()
            request.setAttribute(SHARES_REQUEST, SharesRequest(parameters(request), user, player, id, { files ->
                val previous = SecurityContextHolder.getContext()
                try {
                    SecurityContextHolder.setContext(context)
                    shareService.createShare(request, files)
                } finally {
                    SecurityContextHolder.setContext(previous)
                }
            }, { share -> shareService.getShareUrl(request, share) }))
        } else if (path in PlaylistsController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val jukebox = operation == "createPlaylist" || operation == "updatePlaylist" || operation == "deletePlaylist"
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, jukebox
            )
            val player = if (operation == "getPlaylist") {
                val wrappedRequest = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrappedRequest, response)
            } else null
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val admin = operation == "getPlaylists" && requireNotNull(securityService.getCurrentUser(request)).isAdminRole
            val rawId = when (operation) {
                "getPlaylist", "deletePlaylist" -> mapRestId(request.getParameter("id"))
                "createPlaylist", "updatePlaylist" -> request.getParameter("playlistId")
                else -> null
            }
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(PLAYLISTS_REQUEST, PlaylistsRequest(username, admin, parameters(request), id, player))
        }
        super.service(request, response)
    }

    private fun parameters(request: HttpServletRequest): Parameters = Parameters.build {
        // Security filters may already have consumed a POST body. Use the servlet's parsed parameters.
        request.parameterNames.asSequence().distinct().forEach { name ->
            request.getParameterValues(name)?.let { appendAll(name, it.toList()) }
        }
    }

    private fun mapRestId(id: String?): String? {
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
        const val STARRED_REQUEST = "org.airsonic.player.starredRequest"
        val STARRED_REQUEST_KEY = AttributeKey<StarredRequest>(STARRED_REQUEST)
        const val BOOKMARKS_REQUEST = "org.airsonic.player.bookmarksRequest"
        val BOOKMARKS_REQUEST_KEY = AttributeKey<BookmarksRequest>(BOOKMARKS_REQUEST)
        const val ALBUMS_REQUEST = "org.airsonic.player.albumsRequest"
        val ALBUMS_REQUEST_KEY = AttributeKey<AlbumsRequest>(ALBUMS_REQUEST)
        const val ALBUM_INFO_REQUEST = "org.airsonic.player.albumInfoRequest"
        val ALBUM_INFO_REQUEST_KEY = AttributeKey<AlbumInfoRequest>(ALBUM_INFO_REQUEST)
        const val SHARES_REQUEST = "org.airsonic.player.sharesRequest"
        val SHARES_REQUEST_KEY = AttributeKey<SharesRequest>(SHARES_REQUEST)
        const val PLAYLISTS_REQUEST = "org.airsonic.player.playlistsRequest"
        val PLAYLISTS_REQUEST_KEY = AttributeKey<PlaylistsRequest>(PLAYLISTS_REQUEST)
        const val ARTIST_INFO_REQUEST = "org.airsonic.player.artistInfoRequest"
        val ARTIST_INFO_REQUEST_KEY = AttributeKey<ArtistInfoRequest>(ARTIST_INFO_REQUEST)
        const val SIMILAR_SONGS_REQUEST = "org.airsonic.player.similarSongsRequest"
        val SIMILAR_SONGS_REQUEST_KEY = AttributeKey<SimilarSongsRequest>(SIMILAR_SONGS_REQUEST)
    }
}

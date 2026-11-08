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
import org.airsonic.player.controller.rest.stream.StreamRestController
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.hls.HlsRestController
import org.airsonic.player.controller.rest.hls.HlsSegmentRestController
import org.airsonic.player.service.JWTSecurityService
import com.auth0.jwt.interfaces.DecodedJWT
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.security.core.Authentication
import org.airsonic.player.controller.rest.starred.StarredController
import org.airsonic.player.controller.rest.starred.StarredRequest
import org.airsonic.player.controller.rest.jukebox.JukeboxController
import org.airsonic.player.controller.rest.jukebox.JukeboxRequest
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
import org.airsonic.player.service.MediaStreamService
import org.airsonic.player.service.CoverArtImageService
import org.airsonic.player.controller.rest.avatar.AvatarRestController
import org.airsonic.player.controller.rest.cover.CoverArtActionsController
import org.airsonic.player.controller.rest.tags.TagEditController
import org.airsonic.player.controller.rest.settings.WebsiteSettingsController
import org.airsonic.player.controller.rest.livequeue.LiveQueueController
import org.airsonic.player.controller.rest.cover.CoverArtRestController
import org.airsonic.player.controller.rest.users.UsersController
import org.airsonic.player.controller.rest.users.PasswordController
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.controller.rest.playqueue.PlayQueueController
import org.airsonic.player.controller.rest.playqueue.PlayQueueRequest
import org.airsonic.player.controller.rest.indexes.IndexesController
import org.airsonic.player.controller.rest.indexes.IndexesRequest
import org.airsonic.player.controller.rest.music.genre.SongsByGenreController
import org.airsonic.player.controller.rest.music.genre.SongsByGenreRequest
import org.airsonic.player.controller.rest.music.top.TopSongsController
import org.airsonic.player.controller.rest.music.top.TopSongsRequest
import org.airsonic.player.controller.rest.music.directory.MusicDirectoryController
import org.airsonic.player.controller.rest.music.directory.MusicDirectoryRequest
import org.airsonic.player.controller.rest.music.random.RandomSongsController
import org.airsonic.player.controller.rest.music.random.RandomSongsRequest
import org.airsonic.player.controller.rest.scrobble.ScrobbleController
import org.airsonic.player.controller.rest.scrobble.ScrobbleRequest
import org.airsonic.player.controller.rest.music.song.SongController
import org.airsonic.player.controller.rest.music.song.SongRequest
import org.airsonic.player.controller.rest.videos.VideosController
import org.airsonic.player.controller.rest.videos.VideosRequest
import org.airsonic.player.controller.rest.lyrics.LyricsController
import org.airsonic.player.controller.rest.rating.RatingController
import org.airsonic.player.controller.rest.rating.RatingRequest
import org.airsonic.player.controller.rest.nowplaying.NowPlayingController
import org.airsonic.player.controller.rest.upload.UploadStatusController
import org.airsonic.player.controller.rest.download.DownloadRestController
import org.airsonic.player.controller.rest.download.DownloadRequest
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.DownloadService
import org.springframework.http.ContentDisposition
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.security.access.AccessDeniedException
import org.apache.commons.io.FilenameUtils
import java.nio.charset.StandardCharsets
import java.util.Optional
import org.airsonic.player.i18n.LocaleResolver
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.NetworkService
import org.airsonic.player.security.JWTAuthenticationToken
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
    private val jukeboxController: JukeboxController,
    private val usersController: UsersController,
    private val passwordController: PasswordController,
    private val playQueueController: PlayQueueController,
    private val indexesController: IndexesController,
    private val songsByGenreController: SongsByGenreController,
    private val topSongsController: TopSongsController,
    private val musicDirectoryController: MusicDirectoryController,
    private val randomSongsController: RandomSongsController,
    private val scrobbleController: ScrobbleController,
    private val nowPlayingController: NowPlayingController,
    private val uploadStatusController: UploadStatusController,
    private val songController: SongController,
    private val videosController: VideosController,
    private val lyricsController: LyricsController,
    private val coverArtActionsController: CoverArtActionsController,
    private val tagEditController: TagEditController,
    private val websiteSettingsController: WebsiteSettingsController,
    private val liveQueueController: LiveQueueController,
    private val ratingController: RatingController,
    private val downloadRestController: DownloadRestController,
    private val downloads: DownloadService,
    private val shareService: ShareService,
    private val mediaFileService: MediaFileService,
    private val playerResolver: RestPlayerResolver,
    private val playerService: PlayerService,
    private val securityService: SecurityService,
    private val localeResolver: LocaleResolver,
    private val coverArtRestController: CoverArtRestController,
    private val avatarRestController: AvatarRestController,
    private val hlsRestController: HlsRestController,
    private val hlsSegmentRestController: HlsSegmentRestController,
    private val streamRestController: StreamRestController,
    private val jaxbWriter: JAXBWriter,
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
            val usersRequest = request.getAttribute(USERS_REQUEST) as? UsersRequest
            if (usersRequest != null) call.attributes.put(USERS_REQUEST_KEY, usersRequest)
            val playQueueRequest = request.getAttribute(PLAY_QUEUE_REQUEST) as? PlayQueueRequest
            if (playQueueRequest != null) call.attributes.put(PLAY_QUEUE_REQUEST_KEY, playQueueRequest)
            val scrobbleRequest = request.getAttribute(SCROBBLE_REQUEST) as? ScrobbleRequest
            if (scrobbleRequest != null) call.attributes.put(SCROBBLE_REQUEST_KEY, scrobbleRequest)
            val downloadRequest = request.getAttribute(DOWNLOAD_REQUEST) as? DownloadRequest
            if (downloadRequest != null) call.attributes.put(DOWNLOAD_REQUEST_KEY, downloadRequest)
            val randomSongsRequest = request.getAttribute(RANDOM_SONGS_REQUEST) as? RandomSongsRequest
            if (randomSongsRequest != null) call.attributes.put(RANDOM_SONGS_REQUEST_KEY, randomSongsRequest)
            val musicDirectoryRequest = request.getAttribute(MUSIC_DIRECTORY_REQUEST) as? MusicDirectoryRequest
            if (musicDirectoryRequest != null) call.attributes.put(MUSIC_DIRECTORY_REQUEST_KEY, musicDirectoryRequest)
            val songRequest = request.getAttribute(SONG_REQUEST) as? SongRequest
            if (songRequest != null) call.attributes.put(SONG_REQUEST_KEY, songRequest)
            val videosRequest = request.getAttribute(VIDEOS_REQUEST) as? VideosRequest
            if (videosRequest != null) call.attributes.put(VIDEOS_REQUEST_KEY, videosRequest)
            val ratingRequest = request.getAttribute(RATING_REQUEST) as? RatingRequest
            if (ratingRequest != null) call.attributes.put(RATING_REQUEST_KEY, ratingRequest)
            val topSongsRequest = request.getAttribute(TOP_SONGS_REQUEST) as? TopSongsRequest
            if (topSongsRequest != null) call.attributes.put(TOP_SONGS_REQUEST_KEY, topSongsRequest)
            val genreSongsRequest = request.getAttribute(GENRE_SONGS_REQUEST) as? SongsByGenreRequest
            if (genreSongsRequest != null) call.attributes.put(GENRE_SONGS_REQUEST_KEY, genreSongsRequest)
            val indexesRequest = request.getAttribute(INDEXES_REQUEST) as? IndexesRequest
            if (indexesRequest != null) call.attributes.put(INDEXES_REQUEST_KEY, indexesRequest)
            val jukeboxRequest = request.getAttribute(JUKEBOX_REQUEST) as? JukeboxRequest
            if (jukeboxRequest != null) {
                call.attributes.put(JUKEBOX_REQUEST_KEY, jukeboxRequest)
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
            val liveQueue = request.getAttribute(LIVE_QUEUE_REQUEST) as? LiveQueueController.Request
            if (liveQueue != null) call.attributes.put(LIVE_QUEUE_REQUEST_KEY, liveQueue)
            val uploadStatus = request.getAttribute(UPLOAD_STATUS_REQUEST) as? UploadStatusController.Request
            if (uploadStatus != null) call.attributes.put(UPLOAD_STATUS_REQUEST_KEY, uploadStatus)
            val websiteSettings = request.getAttribute(WEBSITE_SETTINGS_REQUEST) as? WebsiteSettingsController.Request
            if (websiteSettings != null) call.attributes.put(WEBSITE_SETTINGS_REQUEST_KEY, websiteSettings)
            val avatar = request.getAttribute(AVATAR_REQUEST) as? AvatarRestController.Request
            if (avatar != null) call.attributes.put(AVATAR_REQUEST_KEY, avatar)
            val tagEdit = request.getAttribute(TAG_EDIT_REQUEST) as? TagEditController.Request
            if (tagEdit != null) call.attributes.put(TAG_EDIT_REQUEST_KEY, tagEdit)
            val coverActions = request.getAttribute(COVER_ACTIONS_REQUEST) as? CoverArtActionsController.Request
            if (coverActions != null) call.attributes.put(COVER_ACTIONS_REQUEST_KEY, coverActions)
            val parameters = request.getAttribute(REST_PARAMETERS) as? Parameters
            if (parameters != null) {
                call.attributes.put(REST_PARAMETERS_KEY, parameters)
            }
            val hlsSegment = request.getAttribute(HLS_SEGMENT_REQUEST) as? HlsSegmentRestController.Request
            if (hlsSegment != null) call.attributes.put(HLS_SEGMENT_REQUEST_KEY, hlsSegment)
            val hlsRequest = request.getAttribute(HLS_REQUEST) as? HlsRestController.Request
            if (hlsRequest != null) call.attributes.put(HLS_REQUEST_KEY, hlsRequest)
            val stream = request.getAttribute(STREAM_REQUEST) as? StreamRestController.Request
            if (stream != null) call.attributes.put(STREAM_REQUEST_KEY, stream)
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
            usersController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            passwordController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            playQueueController.routes(this) { call -> call.attributes[PLAY_QUEUE_REQUEST_KEY] }
            indexesController.routes(this) { call -> call.attributes[INDEXES_REQUEST_KEY] }
            songsByGenreController.routes(this) { call -> call.attributes[GENRE_SONGS_REQUEST_KEY] }
            downloadRestController.routes(this) { call -> call.attributes[DOWNLOAD_REQUEST_KEY] }
            uploadStatusController.routes(this) { call -> call.attributes[UPLOAD_STATUS_REQUEST_KEY] }
            nowPlayingController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            scrobbleController.routes(this) { call -> call.attributes[SCROBBLE_REQUEST_KEY] }
            randomSongsController.routes(this) { call -> call.attributes[RANDOM_SONGS_REQUEST_KEY] }
            musicDirectoryController.routes(this) { call -> call.attributes[MUSIC_DIRECTORY_REQUEST_KEY] }
            songController.routes(this) { call -> call.attributes[SONG_REQUEST_KEY] }
            videosController.routes(this) { call -> call.attributes[VIDEOS_REQUEST_KEY] }
            liveQueueController.routes(this) { call -> call.attributes[LIVE_QUEUE_REQUEST_KEY] }
            websiteSettingsController.routes(this) { call -> call.attributes[WEBSITE_SETTINGS_REQUEST_KEY] }
            tagEditController.routes(this) { call -> call.attributes[TAG_EDIT_REQUEST_KEY] }
            coverArtRestController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            avatarRestController.routes(this) { call -> call.attributes[AVATAR_REQUEST_KEY] }
            coverArtActionsController.routes(this) { call -> call.attributes[COVER_ACTIONS_REQUEST_KEY] }
            lyricsController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            ratingController.routes(this) { call -> call.attributes[RATING_REQUEST_KEY] }
            topSongsController.routes(this) { call -> call.attributes[TOP_SONGS_REQUEST_KEY] }
            jukeboxController.routes(this) { call -> call.attributes[JUKEBOX_REQUEST_KEY] }
            hlsRestController.routes(this) { call -> call.attributes[HLS_REQUEST_KEY] }
            hlsSegmentRestController.routes(this) { call -> call.attributes[HLS_SEGMENT_REQUEST_KEY] }
            streamRestController.routes(this) { call -> call.attributes[STREAM_REQUEST_KEY] }
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
        if (path.startsWith("/ext/rest-hls/")) {
            val auth = request.userPrincipal as? JWTAuthenticationToken
            val token = auth?.details as? DecodedJWT
            val expected = token?.getClaim(JWTSecurityService.CLAIM_PATH)?.asString()
            val actual = request.requestURI.substring(request.contextPath.length + 1) + "?" + request.queryString
            val valid = try {
                val signed = UriComponentsBuilder.fromUriString(expected ?: "").build()
                val received = UriComponentsBuilder.fromUriString(actual).replaceQueryParam("jwt").build()
                signed.path == received.path && signed.queryParams == received.queryParams
            } catch (_: IllegalArgumentException) { false }
            if (auth == null || !valid) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
        }
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
        } else if (path in UsersController.PATHS || path in PasswordController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(USERS_REQUEST, UsersRequest(requireNotNull(securityService.getCurrentUser(request)), parameters(request)))
        } else if (path in JukeboxController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, true
            )
            val user = requireNotNull(securityService.getCurrentUser(request))
            val player = if (user.isJukeboxRole) {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            request.setAttribute(JUKEBOX_REQUEST, JukeboxRequest(user.username, user.isJukeboxRole, player, parameters(request)))
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
        } else if (path in DownloadRestController.PATHS) {
            val params = parameters(request)
            val rawPlaylist = params["playlist"]
            val rawPlayer = params["player"]
            val playlist = rawPlaylist?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val suppliedPlayer = rawPlayer?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val rawIndices = params.getAll("i")?.flatMap { it.split(',') }
            val indices = rawIndices?.map { it.trim().toIntOrNull() }
            if ((rawPlaylist != null && rawPlaylist.isNotEmpty() && playlist == null) ||
                (rawPlayer != null && rawPlayer.isNotEmpty() && suppliedPlayer == null) ||
                (indices != null && indices.any { it == null })) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? = when (name) {
                    "player" -> playerId?.toString()
                    "id" -> mapRestId(super.getParameter(name))
                    else -> super.getParameter(name)
                }
            }
            val id = mapRestId(params["id"])?.takeIf { StringUtils.isNumeric(it) }?.let { Integer.valueOf(it) }
            val user = requireNotNull(securityService.getUserByName(request.userPrincipal.name))
            if (!user.isDownloadRole) {
                request.setAttribute(DOWNLOAD_REQUEST, DownloadRequest(params, null, ErrorCode.NOT_AUTHORIZED,
                    "${user.username} is not authorized to download files."))
            } else {
                try {
                    val player = playerService.getPlayer(wrapped, response, false, false)
                    val download = downloads.prepare(user, player, Optional.ofNullable(id), playlist, playerId, indices?.filterNotNull())
                    if (download != null && ServletWebRequest(wrapped, response).checkNotModified(download.size.toString(), download.changed)) return
                    val disposition = download?.let { ContentDisposition.builder("attachment").filename(it.filename, StandardCharsets.UTF_8).build().toString() }
                    val type = download?.let { StringUtil.getMimeType(FilenameUtils.getExtension(it.filename)) }
                    request.setAttribute(DOWNLOAD_REQUEST, DownloadRequest(params, download, contentDisposition = disposition, contentType = type))
                } catch (e: AccessDeniedException) {
                    if (path.endsWith(".view")) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN)
                        return
                    }
                    request.setAttribute(DOWNLOAD_REQUEST, DownloadRequest(params, null, ErrorCode.GENERIC, "Request processing failed: $e"))
                }
            }
        } else if (path in NowPlayingController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in ScrobbleController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            request.setAttribute(SCROBBLE_REQUEST, ScrobbleRequest(player, parameters(request)))
        } else if (path in RandomSongsController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(RANDOM_SONGS_REQUEST, RandomSongsRequest(username, player, parameters(request)))
        } else if (path in MusicDirectoryController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(MUSIC_DIRECTORY_REQUEST, MusicDirectoryRequest(username, player, parameters(request)))
        } else if (path in SongController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(SONG_REQUEST, SongRequest(username, player, parametersWithMappedId(request)))
        } else if (path in VideosController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(VIDEOS_REQUEST, VideosRequest(username, player, parameters(request)))
        } else if (path in LyricsController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in RatingController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(RATING_REQUEST, RatingRequest(username, parametersWithMappedId(request)))
        } else if (path in TopSongsController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val securityContext = SecurityContextHolder.getContext()
            request.setAttribute(TOP_SONGS_REQUEST, TopSongsRequest(username, parameters(request)) {
                val previousContext = SecurityContextHolder.getContext()
                try {
                    SecurityContextHolder.setContext(securityContext)
                    playerService.getPlayer(wrapped, response)
                } finally {
                    SecurityContextHolder.setContext(previousContext)
                }
            })
        } else if (path in SongsByGenreController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(GENRE_SONGS_REQUEST, SongsByGenreRequest(username, player, parameters(request)))
        } else if (path in IndexesController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUser(request)).username
            val rawFolderId = request.getParameter("musicFolderId")
            val folderId = if (rawFolderId == null) null else rawFolderId.toIntOrNull() ?: run {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val securityContext = SecurityContextHolder.getContext()
            fun <T> withSecurityContext(action: () -> T): T {
                val previousContext = SecurityContextHolder.getContext()
                return try {
                    SecurityContextHolder.setContext(securityContext)
                    action()
                } finally {
                    SecurityContextHolder.setContext(previousContext)
                }
            }
            request.setAttribute(INDEXES_REQUEST, IndexesRequest(username, parameters(request), folderId,
                { withSecurityContext { playerService.getPlayer(wrapped, response) } }))
        } else if (path in PlayQueueController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val player = if (path.substringAfterLast('/').removeSuffix(".view") == "getPlayQueue") {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            request.setAttribute(PLAY_QUEUE_REQUEST, PlayQueueRequest(username, parameters(request), player))
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
        } else if (path in StreamRestController.PATHS && request.method in setOf("GET", "POST", "HEAD")) {
            val rawId = request.getParameter("id")?.trim()?.takeIf { it.isNotEmpty() }
            val rawBitRate = request.getParameter("maxBitRate")?.trim()?.takeIf { it.isNotEmpty() }
            val rawOffset = request.getParameter("offsetSeconds")?.trim()?.takeIf { it.isNotEmpty() }
            val id = rawId?.toIntOrNull()
            val bitRate = rawBitRate?.toIntOrNull()
            val offset = rawOffset?.toDoubleOrNull()
            if ((rawId != null && id == null) || (rawBitRate != null && bitRate == null) || (rawOffset != null && offset == null)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? = when (name) {
                    "player" -> playerId?.toString()
                    "id" -> mapRestId(super.getParameter(name))
                    else -> super.getParameter(name)
                }
            }
            val authentication = request.userPrincipal as Authentication
            val user = requireNotNull(securityService.getUserByName(authentication.name))
            if (!user.isStreamRole) {
                jaxbWriter.writeErrorResponse(wrapped, response, ErrorCode.NOT_AUTHORIZED,
                    "${user.username} is not authorized to play files.")
                return
            }
            val player = playerService.getPlayer(wrapped, response, false, true)
            val file = request.getParameter("path")?.let { mediaFileService.getMediaFile(it) }
                ?: id?.let { mediaFileService.getMediaFile(it) }
            if (file != null && !securityService.isFolderAccessAllowed(file, user.username)) {
                if (path.endsWith(".view")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
                throw AccessDeniedException("Access to file ${file.id} is forbidden for user ${user.username}")
            }
            val webRequest = ServletWebRequest(wrapped, response)
            request.setAttribute(STREAM_REQUEST, StreamRestController.Request(authentication, player, user,
                request.getParameter("format"), request.getParameter("suffix"), bitRate, id, request.getParameter("path"), offset,
                MediaStreamService.VideoOptions(request.getParameter("maxBitRate"), request.getParameter("timeOffset"),
                    request.getParameter("duration"), request.getParameter("size")), request.getHeader("icy-metadata"),
                java.util.function.BiPredicate { tag, modified -> webRequest.checkNotModified(tag, modified) },
                { response.status }))
        } else if (path in HlsRestController.PATHS && request.method in setOf("GET", "POST", "HEAD")) {
            val rawId = request.getParameter("id")
            if (rawId == null || rawId.isEmpty()) {
                jaxbWriter.writeErrorResponse(request, response, ErrorCode.MISSING_PARAMETER,
                    "Required param (id) is missing")
                return
            }
            val id = rawId.toIntOrNull()
            if (id == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerId = if (path.startsWith("/ext/")) request.getParameter("player")?.toIntOrNull() else
                playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? = when (name) {
                    "player" -> playerId?.toString()
                    "id" -> mapRestId(super.getParameter(name))
                    else -> super.getParameter(name)
                }
            }
            val user = requireNotNull(securityService.getCurrentUser(wrapped))
            if (!user.isStreamRole) {
                jaxbWriter.writeErrorResponse(wrapped, response, ErrorCode.NOT_AUTHORIZED,
                    "${user.username} is not authorized to play files.")
                return
            }
            val authentication = request.userPrincipal as Authentication
            val mediaFile = mediaFileService.getMediaFile(id)
            val player = playerService.getPlayer(wrapped, response)
            if (mediaFile == null || mediaFile.isDirectory) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Media file not found or incorrect type: $id")
                return
            }
            if (!securityService.isFolderAccessAllowed(mediaFile, player.username)) {
                if (path.endsWith(".view")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
                throw AccessDeniedException("Access to file ${mediaFile.id} is forbidden for user ${player.username}")
            }
            val duration = mediaFile.duration
            if (duration == null || duration < 0.0001) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unknown duration for media file: $id")
                return
            }
            val prefix = "ext/rest-hls"
            request.setAttribute(HLS_REQUEST, HlsRestController.Request(authentication, mediaFile, player,
                NetworkService.getBaseUrl(request), prefix, request.getParameterValues("maxBitRate"),
                request.getParameter("size")))
        } else if (path in HlsSegmentRestController.PATHS && request.method in setOf("GET", "HEAD")) {
            val auth = request.userPrincipal as Authentication
            val user = securityService.getUserByName(auth.name)
            if (user == null || !user.isStreamRole) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val id = request.getParameter("id")?.toIntOrNull()
            val playerId = request.getParameter("player")
            val index = request.getParameter("segmentIndex")?.toIntOrNull()
            val bitRate = request.getParameter("maxBitRate")?.toIntOrNull()
            val size = request.getParameter("size")
            val duration = request.getParameter("duration")?.toIntOrNull() ?: 10
            val audioTrack = request.getParameter("audioTrack")?.toIntOrNull()
            if (id == null || playerId?.toIntOrNull() == null || index == null || index < 0 ||
                bitRate == null || bitRate <= 0 || size == null || !size.matches(Regex("[1-9][0-9]*x[1-9][0-9]*")) ||
                duration <= 0 || (request.getParameter("audioTrack") != null && audioTrack == null) ||
                (request.getParameter("duration") != null && request.getParameter("duration").toIntOrNull() == null)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val media = mediaFileService.getMediaFile(id)
            if (media == null || media.isDirectory) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            if (!securityService.isFolderAccessAllowed(media, user.username)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val player = playerService.getPlayer(request, response)
            if (player.id.toString() != playerId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            request.setAttribute(HLS_SEGMENT_REQUEST, HlsSegmentRestController.Request(auth, user, player,
                id, playerId, index, bitRate, size, duration, audioTrack))
        } else if (path in LiveQueueController.PATHS) {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val player = request.getParameter("playerId")?.toIntOrNull()?.let { playerService.getPlayerById(it) }
            request.setAttribute(LIVE_QUEUE_REQUEST, LiveQueueController.Request(
                player?.takeIf { it.username == username }, parameters(request), username))
        } else if (path in UploadStatusController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(UPLOAD_STATUS_REQUEST, UploadStatusController.Request(
                user.username, user.isAdminRole || user.isUploadRole, parameters(request)))
        } else if (path in WebsiteSettingsController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(WEBSITE_SETTINGS_REQUEST, WebsiteSettingsController.Request(user.username, parameters(request)))
        } else if (path in TagEditController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(TAG_EDIT_REQUEST, TagEditController.Request(
                user.username, user.isAdminRole || user.isCoverArtRole, parameters(request)))
        } else if (path in CoverArtActionsController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(COVER_ACTIONS_REQUEST, CoverArtActionsController.Request(
                user.username, user.isAdminRole || user.isCoverArtRole, parameters(request)))
        } else if (path in AvatarRestController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val context = request.servletContext
            request.setAttribute(AVATAR_REQUEST, AvatarRestController.Request(parametersWithMappedId(request)) { context.getResourceAsStream(it) })
        } else if (path in CoverArtRestController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parametersWithMappedId(request))
        }
        super.service(request, response)
    }

    private fun parameters(request: HttpServletRequest): Parameters = Parameters.build {
        // Security filters may already have consumed a POST body. Use the servlet's parsed parameters.
        request.parameterNames.asSequence().distinct().forEach { name ->
            request.getParameterValues(name)?.let { appendAll(name, it.toList()) }
        }
    }

    /**
     * Mirrors the old REST request wrapper: its single {@code id} parameter was
     * resolved from the legacy UTF-8-hex path form before endpoint code read it.
     */
    private fun parametersWithMappedId(request: HttpServletRequest): Parameters = Parameters.build {
        request.parameterNames.asSequence().distinct().forEach { name ->
            val values = request.getParameterValues(name)?.toList().orEmpty()
            if (name == "id" && values.isNotEmpty()) {
                append(name, mapRestId(values.first()) ?: values.first())
            } else {
                appendAll(name, values)
            }
        }
    }

    private fun mapRestId(id: String?): String? {
        if (id == null || id.startsWith(CoverArtImageService.ALBUM_COVERART_PREFIX) ||
            id.startsWith(CoverArtImageService.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)
        ) return id
        return try {
            mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).id.toString()
        } catch (_: Exception) {
            id
        }
    }

    private companion object {
        const val STREAM_REQUEST = "org.airsonic.player.streamRequest"
        val STREAM_REQUEST_KEY = AttributeKey<StreamRestController.Request>(STREAM_REQUEST)
        const val HLS_SEGMENT_REQUEST = "org.airsonic.player.hlsSegmentRequest"
        val HLS_SEGMENT_REQUEST_KEY = AttributeKey<HlsSegmentRestController.Request>(HLS_SEGMENT_REQUEST)
        const val HLS_REQUEST = "org.airsonic.player.hlsRequest"
        val HLS_REQUEST_KEY = AttributeKey<HlsRestController.Request>(HLS_REQUEST)
        const val LIVE_QUEUE_REQUEST = "org.airsonic.player.liveQueueRequest"
        val LIVE_QUEUE_REQUEST_KEY = AttributeKey<LiveQueueController.Request>(LIVE_QUEUE_REQUEST)
        const val UPLOAD_STATUS_REQUEST = "org.airsonic.player.uploadStatusRequest"
        val UPLOAD_STATUS_REQUEST_KEY = AttributeKey<UploadStatusController.Request>(UPLOAD_STATUS_REQUEST)
        const val WEBSITE_SETTINGS_REQUEST = "org.airsonic.player.websiteSettingsRequest"
        val WEBSITE_SETTINGS_REQUEST_KEY = AttributeKey<WebsiteSettingsController.Request>(WEBSITE_SETTINGS_REQUEST)
        const val AVATAR_REQUEST = "org.airsonic.player.avatarRequest"
        val AVATAR_REQUEST_KEY = AttributeKey<AvatarRestController.Request>(AVATAR_REQUEST)
        const val TAG_EDIT_REQUEST = "org.airsonic.player.tagEditRequest"
        val TAG_EDIT_REQUEST_KEY = AttributeKey<TagEditController.Request>(TAG_EDIT_REQUEST)
        const val COVER_ACTIONS_REQUEST = "org.airsonic.player.coverActionsRequest"
        val COVER_ACTIONS_REQUEST_KEY = AttributeKey<CoverArtActionsController.Request>(COVER_ACTIONS_REQUEST)
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
        const val USERS_REQUEST = "org.airsonic.player.usersRequest"
        val USERS_REQUEST_KEY = AttributeKey<UsersRequest>(USERS_REQUEST)
        const val PLAY_QUEUE_REQUEST = "org.airsonic.player.playQueueRequest"
        val PLAY_QUEUE_REQUEST_KEY = AttributeKey<PlayQueueRequest>(PLAY_QUEUE_REQUEST)
        const val SCROBBLE_REQUEST = "org.airsonic.player.scrobbleRequest"
        val SCROBBLE_REQUEST_KEY = AttributeKey<ScrobbleRequest>(SCROBBLE_REQUEST)
        const val DOWNLOAD_REQUEST = "org.airsonic.player.downloadRequest"
        val DOWNLOAD_REQUEST_KEY = AttributeKey<DownloadRequest>(DOWNLOAD_REQUEST)
        const val RANDOM_SONGS_REQUEST = "org.airsonic.player.randomSongsRequest"
        val RANDOM_SONGS_REQUEST_KEY = AttributeKey<RandomSongsRequest>(RANDOM_SONGS_REQUEST)
        const val MUSIC_DIRECTORY_REQUEST = "org.airsonic.player.musicDirectoryRequest"
        val MUSIC_DIRECTORY_REQUEST_KEY = AttributeKey<MusicDirectoryRequest>(MUSIC_DIRECTORY_REQUEST)
        const val SONG_REQUEST = "org.airsonic.player.songRequest"
        val SONG_REQUEST_KEY = AttributeKey<SongRequest>(SONG_REQUEST)
        const val VIDEOS_REQUEST = "org.airsonic.player.videosRequest"
        val VIDEOS_REQUEST_KEY = AttributeKey<VideosRequest>(VIDEOS_REQUEST)
        const val RATING_REQUEST = "org.airsonic.player.ratingRequest"
        val RATING_REQUEST_KEY = AttributeKey<RatingRequest>(RATING_REQUEST)
        const val TOP_SONGS_REQUEST = "org.airsonic.player.topSongsRequest"
        val TOP_SONGS_REQUEST_KEY = AttributeKey<TopSongsRequest>(TOP_SONGS_REQUEST)
        const val GENRE_SONGS_REQUEST = "org.airsonic.player.genreSongsRequest"
        val GENRE_SONGS_REQUEST_KEY = AttributeKey<SongsByGenreRequest>(GENRE_SONGS_REQUEST)
        const val INDEXES_REQUEST = "org.airsonic.player.indexesRequest"
        val INDEXES_REQUEST_KEY = AttributeKey<IndexesRequest>(INDEXES_REQUEST)
        const val JUKEBOX_REQUEST = "org.airsonic.player.jukeboxRequest"
        val JUKEBOX_REQUEST_KEY = AttributeKey<JukeboxRequest>(JUKEBOX_REQUEST)
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

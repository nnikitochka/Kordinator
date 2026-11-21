package org.airsonic.player.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.airsonic.player.runtime.LifecycleRegistry
import org.airsonic.player.runtime.RuntimeCompositionRoot
import org.airsonic.player.runtime.RuntimeConfigurationLoader
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.security.LastEnabledAdministratorException
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.security.LoginRateLimiter
import org.airsonic.player.security.LegacyCredentialEncoderRegistry
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandalonePlayQueueService
import org.airsonic.player.service.StandalonePlayQueue
import org.airsonic.player.service.StandaloneAlbum
import org.airsonic.player.service.StandaloneArtist
import org.airsonic.player.service.StandaloneTrack
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneBookmarkService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.domain.MusicFolder
import java.nio.file.Path
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption
import java.util.Base64

private val authDispatcher = Dispatchers.IO.limitedParallelism(4)
private val scanDispatcher = Dispatchers.IO.limitedParallelism(2)

fun Application.module() {
    routing {
        get("/internal/ktor/ping") {
            call.respondText("pong")
        }
    }
}

/** Minimal new API module used while the application is moving to a standalone Ktor process. */
fun Application.standaloneModule(
    authService: LocalAuthService? = null,
    libraryScanService: StandaloneLibraryScanService? = null,
    playlistService: StandalonePlaylistService? = null,
    favoriteService: StandaloneFavoriteService? = null,
    mediaFolderService: MediaFolderService? = null,
    mediaCatalogService: KtorCoreMediaCatalogService? = null,
    playQueueService: StandalonePlayQueueService? = null,
    bookmarkService: StandaloneBookmarkService? = null,
) {
    val loginRateLimiter = LoginRateLimiter()
    val scanScope = libraryScanService?.let { CoroutineScope(SupervisorJob() + scanDispatcher) }
    if (scanScope != null) monitor.subscribe(ApplicationStopped) { scanScope.cancel() }
    install(Authentication) {
        bearer("server-session") {
            authenticate { credentials ->
                authService?.let { withContext(authDispatcher) { it.authenticate(credentials.token) } }
            }
        }
    }
    routing {
        get("/api/v1/health") {
            call.respondJson(HealthResponse("UP"))
        }
        if (authService != null) {
            route("/api/v1") {
                authenticate("server-session") {
                    get("/search") {
                        if (mediaCatalogService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val query = call.request.queryParameters["query"]?.trim()
                        val offsetValue = call.request.queryParameters["offset"]
                        val limitValue = call.request.queryParameters["limit"]
                        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
                        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 25 else null
                        if (query.isNullOrEmpty() || offset == null || offset < 0 || limit == null || limit !in 1..100) {
                            call.respondJson(ApiError("invalid_request", "Query is required, offset must be non-negative and limit must be between 1 and 100"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val accessibleLibraries = call.catalogLibraryScope()
                        val (matches, total) = withContext(scanDispatcher) {
                            mediaCatalogService.tracks(null, query, limit, offset, accessibleLibraries) to
                                mediaCatalogService.countTracks(null, query, accessibleLibraries)
                        }
                        call.respondJson(StandaloneSearchResponse(query, offset, total, matches.map {
                            TrackResponse(it.id, it.libraryId, it.relativePath, it.title, it.artist, it.album,
                                it.albumArtist, it.durationSeconds, it.sizeBytes, it.modifiedAt?.toString())
                        }))
                    }
                    get("/albums") {
                        if (mediaCatalogService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val requestedLibraryId = call.request.queryParameters["libraryId"]
                        val libraryId = requestedLibraryId?.toLongOrNull()
                        val limitValue = call.request.queryParameters["limit"]
                        val offsetValue = call.request.queryParameters["offset"]
                        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
                        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
                        if ((requestedLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) ||
                            limit == null || limit !in 1..100 || offset == null || offset < 0
                        ) {
                            call.respondJson(ApiError("invalid_request", "libraryId must be between 0 and ${Int.MAX_VALUE}, limit between 1 and 100, and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val accessibleLibraries = call.catalogLibraryScope()
                        if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val albums = withContext(scanDispatcher) {
                            mediaCatalogService.albums(libraryId, limit, offset, accessibleLibraries)
                        }
                        call.respondJson(AlbumPage(albums.map { it.toResponse() }, albums.size, limit, offset))
                    }
                    get("/albums/{id}") {
                        val selector = call.parameters["id"]?.decodeCatalogId()
                        if (selector == null) {
                            call.respondJson(ApiError("invalid_request", "Album ID is invalid"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val service = mediaCatalogService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val accessibleLibraries = call.catalogLibraryScope()
                        if (accessibleLibraries?.contains(selector.first) == false) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val tracks = withContext(scanDispatcher) {
                            service.tracksForAlbum(selector.first, selector.second, accessibleLibraries)
                        }
                        if (tracks.isEmpty()) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondJson(AlbumDetailsResponse(
                            catalogId(selector.first, selector.second), selector.second, tracks.first().albumArtist ?: tracks.first().artist,
                            tracks.sumOf { it.durationSeconds.toLong() }, tracks.map { it.toResponse() },
                        ))
                    }
                    get("/artists") {
                        if (mediaCatalogService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val requestedLibraryId = call.request.queryParameters["libraryId"]
                        val libraryId = requestedLibraryId?.toLongOrNull()
                        val limitValue = call.request.queryParameters["limit"]
                        val offsetValue = call.request.queryParameters["offset"]
                        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
                        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
                        if ((requestedLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) ||
                            limit == null || limit !in 1..100 || offset == null || offset < 0
                        ) {
                            call.respondJson(ApiError("invalid_request", "libraryId must be between 0 and ${Int.MAX_VALUE}, limit between 1 and 100, and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val accessibleLibraries = call.catalogLibraryScope()
                        if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val artists = withContext(scanDispatcher) {
                            mediaCatalogService.artists(libraryId, limit, offset, accessibleLibraries)
                        }
                        call.respondJson(ArtistPage(artists.map { it.toResponse() }, artists.size, limit, offset))
                    }
                    get("/artists/{id}") {
                        val selector = call.parameters["id"]?.decodeCatalogId()
                        if (selector == null) {
                            call.respondJson(ApiError("invalid_request", "Artist ID is invalid"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val service = mediaCatalogService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val accessibleLibraries = call.catalogLibraryScope()
                        if (accessibleLibraries?.contains(selector.first) == false) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val tracks = withContext(scanDispatcher) {
                            service.tracksForArtist(selector.first, selector.second, accessibleLibraries)
                        }
                        if (tracks.isEmpty()) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondJson(ArtistDetailsResponse(
                            catalogId(selector.first, selector.second), selector.second, tracks.map { it.toResponse() },
                        ))
                    }
                    get("/tracks") {
                    if (mediaCatalogService == null) {
                        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val requestedLibraryId = call.request.queryParameters["libraryId"]
                    val libraryId = requestedLibraryId?.toLongOrNull()
                    if (requestedLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) {
                        call.respondJson(ApiError("invalid_request", "libraryId must be between 0 and ${Int.MAX_VALUE}"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val query = call.request.queryParameters["q"]
                    val accessibleLibraries = call.catalogLibraryScope()
                    if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val (requestedTracks, total) = withContext(scanDispatcher) {
                        mediaCatalogService.tracks(libraryId, query, limit, offset, accessibleLibraries) to
                            mediaCatalogService.countTracks(libraryId, query, accessibleLibraries)
                    }
                    val tracks = requestedTracks.map {
                        TrackResponse(
                            it.id, it.libraryId, it.relativePath, it.title, it.artist, it.album, it.albumArtist,
                            it.durationSeconds, it.sizeBytes, it.modifiedAt?.toString(),
                        )
                    }
                    call.respondJson(TrackPage(tracks, tracks.size, total, limit.coerceIn(1, 200), offset.coerceAtLeast(0)))
                }
                    get("/tracks/{id}") {
                        if (mediaCatalogService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId < 0) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val track = withContext(scanDispatcher) {
                            mediaCatalogService.track(trackId, call.catalogLibraryScope())
                        }
                        if (track == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondJson(TrackResponse(track.id, track.libraryId, track.relativePath, track.title,
                            track.artist, track.album, track.albumArtist, track.durationSeconds, track.sizeBytes,
                            track.modifiedAt?.toString()))
                    }
                    get("/tracks/{id}/stream") {
                    val scanService = libraryScanService
                    val catalogService = mediaCatalogService
                    if (scanService == null || catalogService == null) {
                        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val trackId = call.parameters["id"]?.toLongOrNull()
                    if (trackId == null || trackId < 0) {
                        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val catalogTrack = withContext(scanDispatcher) {
                        catalogService.track(trackId, call.catalogLibraryScope())
                    }
                    if (catalogTrack == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val track = withContext(scanDispatcher) {
                        scanService.streamFile(catalogTrack.libraryId, catalogTrack.relativePath)
                    }
                    if (track == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
                    val rangeHeader = call.request.headers[HttpHeaders.Range]
                    val range = if (rangeHeader == null) ByteRange(0, track.sizeBytes - 1) else parseByteRange(rangeHeader, track.sizeBytes)
                    if (range == null) {
                        call.response.headers.append(HttpHeaders.ContentRange, "bytes */${track.sizeBytes}")
                        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                        return@get
                    }
                    val partial = rangeHeader != null
                    if (partial) call.response.headers.append(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/${track.sizeBytes}")
                    val length = if (track.sizeBytes == 0L && !partial) 0L else range.end - range.start + 1
                    call.respondBytesWriter(
                        contentType = ContentType.parse(track.contentType),
                        status = if (partial) HttpStatusCode.PartialContent else HttpStatusCode.OK,
                        contentLength = length,
                    ) {
                        FileChannel.open(track.path, StandardOpenOption.READ).use { file ->
                            file.position(range.start)
                            val buffer = ByteBuffer.allocate(64 * 1024)
                            var remaining = length
                            while (remaining > 0) {
                                buffer.clear()
                                buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                                val read = withContext(Dispatchers.IO) { file.read(buffer) }
                                if (read <= 0) break
                                writeFully(buffer.array(), 0, read)
                                remaining -= read
                            }
                        }
                    }
                }
                    get("/tracks/{id}/artwork") {
                    val scanService = libraryScanService
                    val catalogService = mediaCatalogService
                    if (scanService == null || catalogService == null) {
                        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val trackId = call.parameters["id"]?.toLongOrNull()
                    if (trackId == null || trackId < 0) {
                        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val catalogTrack = withContext(scanDispatcher) {
                        catalogService.track(trackId, call.catalogLibraryScope())
                    }
                    if (catalogTrack == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val artwork = withContext(scanDispatcher) {
                        scanService.artwork(catalogTrack.libraryId, catalogTrack.relativePath)
                    }
                    if (artwork == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                    call.respondBytes(artwork.bytes, ContentType.parse(artwork.contentType))
                    }
                }
                post("/auth/login") {
                    val request = try {
                        jsonMapper.readValue(call.receiveText(), LoginRequest::class.java)
                    } catch (_: Exception) {
                        call.respondJson(ApiError("invalid_request", "Expected username and password"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    if (request.username.length > 64 || request.password.length > MAX_PASSWORD_LENGTH) {
                        call.respondJson(ApiError("invalid_request", "Username or password is too long"), HttpStatusCode.BadRequest)
                        return@post
                    }
                    val source = call.request.local.remoteHost
                    if (loginRateLimiter.isLimited(source)) {
                        call.respondJson(ApiError("login_rate_limited", "Too many attempts; try again later"), HttpStatusCode.TooManyRequests)
                        return@post
                    }
                    val session = withContext(authDispatcher) { authService.login(request.username, request.password) }
                    if (session == null) {
                        loginRateLimiter.recordFailure(source)
                        call.respondJson(ApiError("invalid_credentials", "Invalid username or password"), HttpStatusCode.Unauthorized)
                    } else {
                        loginRateLimiter.clear(source)
                        call.respondJson(LoginResponse(session.token, expiresAt = session.expiresAt.toString()))
                    }
                }
                authenticate("server-session") {
                    get("/favorites/tracks") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val service = favoriteService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val limitValue = call.request.queryParameters["limit"]
                        val offsetValue = call.request.queryParameters["offset"]
                        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
                        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
                        if (limit == null || limit !in 1..100 || offset == null || offset < 0) {
                            call.respondJson(ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val accessibleLibraries = user.catalogLibraryScope()
                        val tracks = withContext(scanDispatcher) { service.list(user.id, limit, offset, accessibleLibraries) }
                        call.respondJson(FavoriteTrackPage(
                            tracks.map { it.toResponse() }, tracks.size, service.count(user.id, accessibleLibraries), limit, offset,
                        ))
                    }
                    get("/favorites/albums") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val service = favoriteService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val (limit, offset) = call.favoritePageParameters() ?: run {
                            call.respondJson(ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val favorites = withContext(scanDispatcher) { service.albums(user.id, user.catalogLibraryScope()) }
                        call.respondJson(FavoriteAlbumPage(
                            favorites.drop(offset).take(limit).map { it.toResponse() }, favorites.size, favorites.size.toLong(), limit, offset,
                        ))
                    }
                    get("/favorites/artists") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val service = favoriteService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val (limit, offset) = call.favoritePageParameters() ?: run {
                            call.respondJson(ApiError("invalid_request", "Limit must be between 1 and 100 and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val favorites = withContext(scanDispatcher) { service.artists(user.id, user.catalogLibraryScope()) }
                        call.respondJson(FavoriteArtistPage(
                            favorites.drop(offset).take(limit).map { it.toResponse() }, favorites.size, favorites.size.toLong(), limit, offset,
                        ))
                    }
                    put("/tracks/{id}/favorite") {
                        setFavorite(call, favoriteService, true)
                    }
                    delete("/tracks/{id}/favorite") {
                        setFavorite(call, favoriteService, false)
                    }
                    put("/albums/{id}/favorite") {
                        setCatalogFavorite(call, favoriteService, favorite = true, artist = false)
                    }
                    delete("/albums/{id}/favorite") {
                        setCatalogFavorite(call, favoriteService, favorite = false, artist = false)
                    }
                    put("/artists/{id}/favorite") {
                        setCatalogFavorite(call, favoriteService, favorite = true, artist = true)
                    }
                    delete("/artists/{id}/favorite") {
                        setCatalogFavorite(call, favoriteService, favorite = false, artist = true)
                    }
                    get("/playlists") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (playlistService == null) {
                            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val accessibleLibraries = user.catalogLibraryScope()
                        val playlists = withContext(scanDispatcher) { playlistService.list(user.id, accessibleLibraries) }
                            .map { PlaylistSummaryResponse(it.id, it.name, it.createdAt.toString(), it.updatedAt.toString(), it.trackCount) }
                        call.respondJson(playlists)
                    }
                    post("/playlists") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        if (playlistService == null) {
                            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@post
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SavePlaylistRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected name and optional trackIds"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        try {
                            val playlist = withContext(scanDispatcher) {
                                playlistService.create(user.id, request.name, request.trackIds, user.catalogLibraryScope())
                            }
                            call.respondJson(playlist.toResponse(), HttpStatusCode.Created)
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_playlist", "Playlist name or track IDs are invalid"), HttpStatusCode.BadRequest)
                        }
                    }
                    get("/playlists/{id}") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val service = playlistService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val playlistId = call.parameters["id"]?.toLongOrNull()
                        if (playlistId == null || playlistId < 0) {
                            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val playlist = withContext(scanDispatcher) {
                            service.get(user.id, playlistId, user.catalogLibraryScope())
                        }
                        if (playlist == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(playlist.toResponse())
                    }
                    put("/playlists/{id}") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val playlistId = call.parameters["id"]?.toLongOrNull()
                        if (playlistId == null || playlistId < 0) {
                            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val service = playlistService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SavePlaylistRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected name and trackIds"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        try {
                            val playlist = withContext(scanDispatcher) {
                                service.update(user.id, playlistId, request.name, request.trackIds, user.catalogLibraryScope())
                            }
                            if (playlist == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(playlist.toResponse())
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_playlist", "Playlist name or track IDs are invalid"), HttpStatusCode.BadRequest)
                        }
                    }
                    delete("/playlists/{id}") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        val service = playlistService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@delete
                        }
                        val playlistId = call.parameters["id"]?.toLongOrNull()
                        if (playlistId == null || playlistId < 0) {
                            call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@delete
                        }
                        val deleted = withContext(scanDispatcher) { service.delete(user.id, playlistId) }
                        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
                    }
                    get("/me") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        call.respondJson(CurrentUserResponse(user.id, user.username, user.role))
                    }
                    get("/me/play-queue") {
                        val service = playQueueService
                        val catalog = mediaCatalogService
                        if (service == null || catalog == null) {
                            call.respondJson(ApiError("unavailable", "Playback queue storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val queue = withContext(authDispatcher) { service.get(user.id) }
                        call.respondJson(queue.toResponse(catalog, user.catalogLibraryScope()))
                    }
                    put("/me/play-queue") {
                        val service = playQueueService
                        val catalog = mediaCatalogService
                        if (service == null || catalog == null) {
                            call.respondJson(ApiError("unavailable", "Playback queue storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@put
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SavePlayQueueRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected trackIds and optional currentTrackId and positionMillis"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val queue = try {
                            withContext(scanDispatcher) {
                                service.save(
                                    user.id,
                                    request.trackIds,
                                    request.currentTrackId,
                                    request.positionMillis,
                                    user.catalogLibraryScope(),
                                )
                            }
                        } catch (failure: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_queue", failure.message ?: "Queue is invalid"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        call.respondJson(queue.toResponse(catalog, user.catalogLibraryScope()))
                    }
                    delete("/me/play-queue") {
                        val service = playQueueService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Playback queue storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@delete
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        withContext(authDispatcher) { service.clear(user.id) }
                        call.respond(HttpStatusCode.NoContent)
                    }
                    get("/me/bookmarks") {
                        val service = bookmarkService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val items = withContext(scanDispatcher) { service.list(user.id, user.catalogLibraryScope()) }
                        call.respondJson(BookmarkPage(items.map { bookmark ->
                            BookmarkResponse(
                                bookmark.track.toResponse(), bookmark.positionMillis, bookmark.comment,
                                bookmark.createdAt.toString(), bookmark.updatedAt.toString(),
                            )
                        }, items.size))
                    }
                    put("/tracks/{id}/bookmark") {
                        val service = bookmarkService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@put
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId < 0) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SetBookmarkRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected non-negative positionMillis and optional comment"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val saved = try {
                            withContext(scanDispatcher) {
                                service.set(user.id, trackId, request.positionMillis, request.comment, user.catalogLibraryScope())
                            }
                        } catch (failure: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_request", failure.message ?: "Bookmark is invalid"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
                    }
                    delete("/tracks/{id}/bookmark") {
                        val service = bookmarkService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Bookmark storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@delete
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId < 0) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@delete
                        }
                        val deleted = withContext(authDispatcher) { service.delete(user.id, trackId) }
                        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
                    }
                    put("/me/password") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), ChangePasswordRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected currentPassword and newPassword"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (request.currentPassword.length > MAX_PASSWORD_LENGTH || request.newPassword.length > MAX_PASSWORD_LENGTH) {
                            call.respondJson(ApiError("invalid_request", "Password is too long"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val changed = try {
                            withContext(authDispatcher) {
                                authService.changePassword(user.id, request.currentPassword, request.newPassword)
                            }
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_password", "New password does not meet the requirements"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (changed) call.respond(HttpStatusCode.NoContent) else {
                            call.respondJson(ApiError("invalid_password", "Current password is invalid"), HttpStatusCode.BadRequest)
                        }
                    }
                    post("/auth/logout") {
                        val token = call.request.headers[HttpHeaders.Authorization]
                            ?.removePrefix("Bearer ")
                            ?.takeIf { it != call.request.headers[HttpHeaders.Authorization] }
                        if (token != null) withContext(authDispatcher) { authService.logout(token) }
                        call.respond(HttpStatusCode.NoContent)
                    }
                    get("/admin/users") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val users = withContext(authDispatcher) {
                            authService.users().map { AdminUserResponse(it.id, it.username, it.role, it.enabled) }
                        }
                        call.respondJson(users)
                    }
                    post("/admin/users") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@post
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), CreateUserRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected username, password and optional role"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        try {
                            val id = withContext(authDispatcher) { authService.createUser(request.username, request.password, request.role) }
                            call.respondJson(CurrentUserResponse(id, request.username, request.role), HttpStatusCode.Created)
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_account", "Username, password or role is invalid"), HttpStatusCode.BadRequest)
                        } catch (failure: java.sql.SQLException) {
                            if (failure.sqlState?.startsWith("23") == true) {
                                call.respondJson(ApiError("account_exists", "An account with that username already exists"), HttpStatusCode.Conflict)
                            } else {
                                throw failure
                            }
                        }
                    }
                    put("/admin/users/{id}") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@put
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null || targetId < 0) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), RenameUserRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected username"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        try {
                            val renamed = withContext(authDispatcher) { authService.renameUser(targetId, request.username) }
                            if (renamed) call.respond(HttpStatusCode.NoContent) else {
                                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                            }
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_username", "Username must contain 3 to 64 letters, digits, dots, underscores or hyphens"), HttpStatusCode.BadRequest)
                        } catch (failure: java.sql.SQLException) {
                            if (failure.sqlState?.startsWith("23") == true) {
                                call.respondJson(ApiError("username_exists", "An account with that username already exists"), HttpStatusCode.Conflict)
                            } else {
                                throw failure
                            }
                        }
                    }
                    delete("/admin/users/{id}") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@delete
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null || targetId < 0) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@delete
                        }
                        if (targetId == actor.id) {
                            call.respondJson(ApiError("invalid_operation", "Cannot delete the current administrator account"), HttpStatusCode.Conflict)
                            return@delete
                        }
                        try {
                            val deleted = withContext(authDispatcher) { authService.deleteUser(targetId) }
                            if (deleted) call.respond(HttpStatusCode.NoContent) else {
                                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                            }
                        } catch (_: LastEnabledAdministratorException) {
                            call.respondJson(ApiError("last_administrator", "At least one enabled administrator must remain"), HttpStatusCode.Conflict)
                        }
                    }
                    put("/admin/users/{id}/role") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@put
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (targetId == actor.id) {
                            call.respondJson(ApiError("invalid_operation", "Cannot change the current administrator role"), HttpStatusCode.Conflict)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SetUserRoleRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected role"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        try {
                            val updated = withContext(authDispatcher) { authService.setUserRole(targetId, request.role) }
                            if (updated) call.respond(HttpStatusCode.NoContent) else {
                                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                            }
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_role", "Role must be USER or ADMIN"), HttpStatusCode.BadRequest)
                        } catch (_: LastEnabledAdministratorException) {
                            call.respondJson(ApiError("last_administrator", "At least one enabled administrator must remain"), HttpStatusCode.Conflict)
                        }
                    }
                    put("/admin/users/{id}/enabled") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@put
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (targetId == actor.id) {
                            call.respondJson(ApiError("invalid_operation", "Cannot disable the current administrator account"), HttpStatusCode.Conflict)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SetUserEnabledRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected enabled"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val changed = withContext(authDispatcher) { authService.setUserEnabled(targetId, request.enabled) }
                        if (changed) call.respond(HttpStatusCode.NoContent) else {
                            call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                        }
                    }
                    get("/admin/users/{id}/libraries") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val libraries = withContext(authDispatcher) { authService.allowedLibraries(targetId) }
                        if (libraries == null) call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                        else call.respondJson(libraries.sorted())
                    }
                    put("/admin/users/{id}/libraries") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@put
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), SetUserLibrariesRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected a libraryIds array"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val validLibraryIds = mediaFolderService?.let { service ->
                            withContext(authDispatcher) { service.getAllMusicFolders(true, true, true) }
                                .mapNotNull { it.id?.toLong() }.toSet()
                        }.orEmpty()
                        if (!validLibraryIds.containsAll(request.libraryIds)) {
                            call.respondJson(ApiError("invalid_library", "One or more libraries do not exist"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val updated = withContext(authDispatcher) {
                            authService.setAllowedLibraries(targetId, request.libraryIds)
                        }
                        if (updated) call.respond(HttpStatusCode.NoContent) else {
                            call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                        }
                    }
                    get("/admin/libraries") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val folders = mediaFolderService?.let { service ->
                            withContext(authDispatcher) { service.getAllMusicFolders(true, true) }
                                .sortedWith(compareBy<MusicFolder> { it.name }.thenBy { it.id })
                                .map { it.toLibraryResponse() }
                        }.orEmpty()
                        call.respondJson(folders)
                    }
                    put("/admin/libraries/{id}") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@put
                        }
                        val folderService = mediaFolderService
                        if (folderService == null) {
                            call.respondJson(ApiError("unavailable", "Music folder storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@put
                        }
                        val libraryId = call.parameters["id"]?.toIntOrNull()
                        if (libraryId == null || libraryId <= 0) {
                            call.respondJson(ApiError("invalid_request", "Library ID must be a positive number"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), UpdateLibraryRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected a name"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val normalizedName = request.name.trim()
                        if (normalizedName.isEmpty() || normalizedName.length > 120) {
                            call.respondJson(ApiError("invalid_library", "Library name must contain 1 to 120 characters"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val folders = withContext(authDispatcher) { folderService.getAllMusicFolders(true, true, true) }
                        val folder = folders.firstOrNull { it.id == libraryId }
                        if (folder == null) {
                            call.respondJson(ApiError("library_not_found", "Library does not exist"), HttpStatusCode.NotFound)
                            return@put
                        }
                        if (folders.any { it.id != libraryId && it.name.equals(normalizedName, ignoreCase = true) }) {
                            call.respondJson(ApiError("library_exists", "A library with that name already exists"), HttpStatusCode.Conflict)
                            return@put
                        }
                        folder.name = normalizedName
                        withContext(authDispatcher) { folderService.updateMusicFolder(folder) }
                        call.respondJson(folder.toLibraryResponse())
                    }
                    post("/admin/libraries") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@post
                        }
                        val folderService = mediaFolderService
                        if (folderService == null) {
                            call.respondJson(ApiError("unavailable", "Music folder storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@post
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), CreateLibraryRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected name and path"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val normalizedName = request.name.trim()
                        if (normalizedName.isEmpty() || normalizedName.length > 120 || request.path.isBlank()) {
                            call.respondJson(ApiError("invalid_library", "Library name and path are required"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val path = try {
                            Path.of(request.path).toRealPath()
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_library", "Library path is not accessible"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
                            call.respondJson(ApiError("invalid_library", "Library path must be a readable directory"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val existingFolders = withContext(authDispatcher) { folderService.getAllMusicFolders(true, true, true) }
                        if (existingFolders.any { it.name.equals(normalizedName, ignoreCase = true) }) {
                            call.respondJson(ApiError("library_exists", "A library with that name already exists"), HttpStatusCode.Conflict)
                            return@post
                        }
                        val folder = MusicFolder(null, path, normalizedName, MusicFolder.Type.MEDIA, true, java.time.Instant.now())
                        try {
                            withContext(authDispatcher) {
                                folderService.createMusicFolder(folder)
                            }
                            call.respondJson(folder.toLibraryResponse(), HttpStatusCode.Created)
                        } catch (failure: IllegalArgumentException) {
                            val overlap = failure.message?.contains("overlap", ignoreCase = true) == true
                            call.respondJson(
                                ApiError(if (overlap) "library_exists" else "invalid_library", if (overlap) "Library path overlaps an existing music folder" else "Library path is invalid"),
                                if (overlap) HttpStatusCode.Conflict else HttpStatusCode.BadRequest,
                            )
                        }
                    }
                    post("/admin/libraries/{id}/scan") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@post
                        }
                        if (libraryScanService == null || scanScope == null) {
                            call.respondJson(ApiError("unavailable", "Library scanning is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@post
                        }
                        val libraryId = call.parameters["id"]?.toLongOrNull()
                        if (libraryId == null) {
                            call.respondJson(ApiError("invalid_request", "Library ID must be a number"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val scan = try {
                            withContext(scanDispatcher) { libraryScanService.enqueue(libraryId) }
                        } catch (failure: IllegalArgumentException) {
                            val status = if (failure.message?.contains("does not exist") == true) {
                                HttpStatusCode.NotFound
                            } else {
                                HttpStatusCode.Conflict
                            }
                            call.respondJson(ApiError("scan_unavailable", failure.message ?: "Unable to start scan"), status)
                            return@post
                        }
                        scanScope.launch { withContext(scanDispatcher) { libraryScanService.run(scan.id) } }
                        call.respondJson(scan.toResponse(), HttpStatusCode.Accepted)
                    }
                    get("/admin/libraries/{id}/scan") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val libraryId = call.parameters["id"]?.toLongOrNull()
                        if (libraryId == null) {
                            call.respondJson(ApiError("invalid_request", "Library ID must be a number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val scan = libraryScanService?.let { withContext(scanDispatcher) { it.latestScan(libraryId) } }
                        if (scan == null) call.respond(HttpStatusCode.NotFound) else call.respondJson(scan.toResponse())
                    }
                }
            }
        }
    }
}

data class HealthResponse(val status: String)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresAt: String)
data class CurrentUserResponse(val id: Long, val username: String, val role: String)
data class AdminUserResponse(val id: Long, val username: String, val role: String, val enabled: Boolean)
data class ApiError(val code: String, val message: String)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class SavePlayQueueRequest(
    val trackIds: List<Long> = emptyList(),
    val currentTrackId: Long? = null,
    val positionMillis: Long = 0,
)
data class PlayQueueResponse(
    val trackIds: List<Long>,
    val currentTrackId: Long?,
    val positionMillis: Long,
    val updatedAt: String?,
    val tracks: List<PlayQueueTrackResponse>,
)
data class PlayQueueTrackResponse(val id: Long, val title: String, val artist: String?, val album: String?, val durationSeconds: Int)
data class BookmarkPage(val items: List<BookmarkResponse>, val count: Int)
data class BookmarkResponse(
    val track: TrackResponse,
    val positionMillis: Long,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String,
)
data class SetBookmarkRequest(val positionMillis: Long, val comment: String? = null)
data class CreateUserRequest(val username: String, val password: String, val role: String = "USER")
data class RenameUserRequest(val username: String)
data class SetUserEnabledRequest(val enabled: Boolean)
data class SetUserRoleRequest(val role: String)
data class SetUserLibrariesRequest(val libraryIds: Set<Long>)
data class CreateLibraryRequest(val name: String, val path: String)
data class UpdateLibraryRequest(val name: String)
data class LibraryResponse(val id: Long, val name: String, val path: String, val enabled: Boolean, val createdAt: String)
private fun MusicFolder.toLibraryResponse() = LibraryResponse(
    id = requireNotNull(id) { "Music folder has not been persisted" }.toLong(),
    name = name,
    path = path.toString(),
    enabled = isEnabled,
    createdAt = changed.toString(),
)
data class TrackResponse(
    val id: Long,
    val libraryId: Long,
    val relativePath: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val modifiedAt: String?,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
)
data class StandaloneSearchResponse(val query: String, val offset: Int, val totalHits: Long, val tracks: List<TrackResponse>)
data class TrackPage(val items: List<TrackResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class AlbumSummaryResponse(
    val id: String, val libraryId: Long, val name: String, val artist: String?, val year: Int?, val genre: String?,
    val trackCount: Long, val durationSeconds: Long,
)
data class AlbumDetailsResponse(
    val id: String, val name: String, val artist: String?, val durationSeconds: Long, val tracks: List<TrackResponse>,
)
data class ArtistSummaryResponse(
    val id: String, val libraryId: Long, val name: String, val albumCount: Long, val trackCount: Long,
)
data class ArtistDetailsResponse(val id: String, val name: String, val tracks: List<TrackResponse>)
data class AlbumPage(val items: List<AlbumSummaryResponse>, val count: Int, val limit: Int, val offset: Int)
data class ArtistPage(val items: List<ArtistSummaryResponse>, val count: Int, val limit: Int, val offset: Int)
data class FavoriteTrackPage(val items: List<TrackResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class FavoriteAlbumPage(val items: List<AlbumSummaryResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class FavoriteArtistPage(val items: List<ArtistSummaryResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class ScanResponse(
    val id: String,
    val libraryId: Long,
    val state: String,
    val tracksScanned: Long,
    val errorCount: Long,
    val startedAt: String,
    val finishedAt: String?,
)

private fun StandaloneAlbum.toResponse() = AlbumSummaryResponse(
    catalogId(libraryId, name), libraryId, name, artist, year, genre, trackCount, durationSeconds,
)

private fun StandaloneArtist.toResponse() = ArtistSummaryResponse(
    catalogId(libraryId, name), libraryId, name, albumCount, trackCount,
)

private fun StandaloneTrack.toResponse() = TrackResponse(
    id, libraryId, relativePath, title, artist, album, albumArtist, durationSeconds, sizeBytes, modifiedAt?.toString(),
    discNumber, trackNumber, year, genre,
)

private fun catalogId(libraryId: Long, name: String): String =
    "$libraryId.${Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))}"

private fun String.decodeCatalogId(): Pair<Long, String>? {
    return try {
        val separator = indexOf('.')
        if (separator < 1 || separator == lastIndex) return null
        val libraryId = substring(0, separator).toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val name = Base64.getUrlDecoder().decode(substring(separator + 1)).toString(Charsets.UTF_8)
        if (name.isBlank()) null else libraryId to name
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun LocalUserPrincipal.catalogLibraryScope(): Set<Long>? =
    if (role == "ADMIN") null else allowedLibraryIds

private fun ApplicationCall.catalogLibraryScope(): Set<Long>? {
    val user = principal<LocalUserPrincipal>() ?: return emptySet()
    return user.catalogLibraryScope()
}

private suspend fun setFavorite(call: ApplicationCall, service: StandaloneFavoriteService?, favorite: Boolean) {
    val user = call.principal<LocalUserPrincipal>()
        ?: return call.respond(HttpStatusCode.Unauthorized)
    if (service == null) {
        call.respondJson(ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val trackId = call.parameters["id"]?.toLongOrNull()
    if (trackId == null || trackId < 0) {
        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
        return
    }
    val updated = withContext(scanDispatcher) { service.set(user.id, trackId, favorite, user.catalogLibraryScope()) }
    if (updated) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
}

private suspend fun setCatalogFavorite(
    call: ApplicationCall,
    service: StandaloneFavoriteService?,
    favorite: Boolean,
    artist: Boolean,
) {
    val user = call.principal<LocalUserPrincipal>()
        ?: return call.respond(HttpStatusCode.Unauthorized)
    if (service == null) {
        call.respondJson(ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val selector = call.parameters["id"]?.decodeCatalogId()
    if (selector == null || selector.first > Int.MAX_VALUE.toLong()) {
        call.respondJson(ApiError("invalid_request", "Catalog ID is invalid"), HttpStatusCode.BadRequest)
        return
    }
    val saved = try {
        withContext(scanDispatcher) {
            if (artist) service.setArtist(user.id, selector.first, selector.second, favorite, user.catalogLibraryScope())
            else service.setAlbum(user.id, selector.first, selector.second, favorite, user.catalogLibraryScope())
        }
    } catch (failure: IllegalArgumentException) {
        call.respondJson(ApiError("invalid_request", failure.message ?: "Catalog favorite is invalid"), HttpStatusCode.BadRequest)
        return
    }
    if (!saved) {
        call.respond(HttpStatusCode.NotFound)
    } else {
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun ApplicationCall.favoritePageParameters(): Pair<Int, Int>? {
    val limitValue = request.queryParameters["limit"]
    val offsetValue = request.queryParameters["offset"]
    val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
    val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
    if (limit == null || limit !in 1..100 || offset == null || offset < 0) return null
    return limit to offset
}
data class SavePlaylistRequest(val name: String, val trackIds: List<Long> = emptyList())
data class PlaylistSummaryResponse(val id: Long, val name: String, val createdAt: String, val updatedAt: String, val trackCount: Int)
data class PlaylistTrackResponse(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int,
)
data class PlaylistResponse(
    val id: Long,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val trackIds: List<Long>,
    val tracks: List<PlaylistTrackResponse>,
)

private data class ByteRange(val start: Long, val end: Long)

private fun parseByteRange(header: String, size: Long): ByteRange? {
    if (size <= 0 || !header.startsWith("bytes=") || header.contains(',')) return null
    val value = header.removePrefix("bytes=").trim()
    val separator = value.indexOf('-')
    if (separator < 0 || separator != value.lastIndexOf('-')) return null
    val first = value.substring(0, separator)
    val last = value.substring(separator + 1)
    return when {
        first.isEmpty() -> {
            val suffixLength = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
            ByteRange((size - suffixLength).coerceAtLeast(0), size - 1)
        }
        else -> {
            val start = first.toLongOrNull()?.takeIf { it >= 0 && it < size } ?: return null
            val end = if (last.isEmpty()) size - 1 else last.toLongOrNull()?.takeIf { it >= start }?.coerceAtMost(size - 1) ?: return null
            ByteRange(start, end)
        }
    }
}

private const val MAX_PASSWORD_LENGTH = 1_024

private val jsonMapper = jacksonObjectMapper()

private suspend fun ApplicationCall.respondJson(value: Any, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(jsonMapper.writeValueAsString(value), ContentType.Application.Json, status)
}

private fun org.airsonic.player.service.LibraryScan.toResponse() = ScanResponse(
    id, libraryId, state, tracksScanned, errorCount, startedAt.toString(), finishedAt?.toString(),
)

private fun org.airsonic.player.service.StandalonePlaylist.toResponse() = PlaylistResponse(
    id,
    name,
    createdAt.toString(),
    updatedAt.toString(),
    tracks.map { it.id },
    tracks.map { PlaylistTrackResponse(it.id, it.title, it.artist, it.album, it.durationSeconds) },
)

private suspend fun StandalonePlayQueue.toResponse(
    mediaCatalogService: KtorCoreMediaCatalogService,
    accessibleLibraryIds: Set<Long>?,
): PlayQueueResponse {
    val tracksById = withContext(scanDispatcher) {
        mediaCatalogService.tracksByCoreIds(trackIds.distinct(), accessibleLibraryIds)
    }.associateBy { it.id }
    val visibleTracks = trackIds.mapNotNull { tracksById[it] }
    return PlayQueueResponse(
        trackIds = visibleTracks.map { it.id },
        currentTrackId = currentTrackId?.takeIf { it in tracksById },
        positionMillis = positionMillis,
        updatedAt = updatedAt?.toString(),
        tracks = visibleTracks.map { PlayQueueTrackResponse(it.id, it.title, it.artist, it.album, it.durationSeconds) },
    )
}

fun main(args: Array<String>) {
    val configuration = RuntimeConfigurationLoader().load(args, System.getProperties(), System.getenv(), null)
    val host = configuration.getProperty("airsonic.http.host", "0.0.0.0") ?: "0.0.0.0"
    val port = configuration.getIntProperty("airsonic.http.port", 8080)
    val lifecycle = LifecycleRegistry()
    Runtime.getRuntime().addShutdownHook(Thread({ lifecycle.shutdown() }, "airsonic-shutdown"))
    val runtime = RuntimeCompositionRoot(configuration, lifecycle)
    lateinit var standaloneDatabase: StandaloneDatabase
    try {
        standaloneDatabase = StandaloneDatabase.connect(configuration)
        lifecycle.registerShutdown("standaloneDatabase", standaloneDatabase)
        val legacyDataSource = runtime.createDataSource()
        runtime.migrateDatabase()
        runtime.createPersistenceGraph(legacyDataSource)
        StandaloneDatabase.migrate(standaloneDatabase.dataSource)
    } catch (failure: Throwable) {
        lifecycle.shutdown()
        throw failure
    }
    val authService = LocalAuthService(standaloneDatabase.dataSource)
    val libraryScanService: StandaloneLibraryScanService
    if (configuration.getBooleanProperty("airsonic.create-admin", false)) {
        try {
            val console = System.console()
                ?: throw IllegalStateException("--airsonic.create-admin requires an interactive terminal")
            val username = console.readLine("Administrator username: ").trim()
            val password = console.readPassword("Administrator password (minimum 12 characters): ")
            val confirmation = console.readPassword("Confirm administrator password: ")
            try {
                require(password.contentEquals(confirmation)) { "Passwords do not match" }
                authService.createInitialAdmin(username, String(password))
                console.printf("Administrator created. Start the server without --airsonic.create-admin.%n")
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
            }
        } finally {
            lifecycle.shutdown()
        }
        return
    }
    val legacyGraph = runtime.createPersistenceGraph(runtime.createDataSource()).daos
    val settingsService = runtime.createSettingsService()
    val mediaFolderService = runtime.createMediaFolderService()
    val mediaCatalogService = KtorCoreMediaCatalogService(runtime.createDataSource(), standaloneDatabase.dataSource)
    val playlistService = StandalonePlaylistService(standaloneDatabase.dataSource, mediaCatalogService)
    val favoriteService = StandaloneFavoriteService(standaloneDatabase.dataSource, mediaCatalogService)
    val playQueueService = StandalonePlayQueueService(standaloneDatabase.dataSource, mediaCatalogService)
    val bookmarkService = StandaloneBookmarkService(standaloneDatabase.dataSource, mediaCatalogService)
    val securityService = runtime.createSecurityService(
        legacyGraph.userDao,
        settingsService,
        mediaFolderService,
        LegacyCredentialEncoderRegistry(settingsService),
    )
    val coverArtService = runtime.createCoverArtService(legacyGraph.coverArtDao)
    val mediaFileService = runtime.createMediaFileService(
        securityService,
        settingsService,
        legacyGraph.mediaFileDao,
        legacyGraph.albumDao,
        runtime.createJaudiotaggerParser(),
        runtime.createMetaDataParserFactory(),
        coverArtService,
        runtime.createLocaleService(settingsService),
    )
    val searchIndex = runtime.createIndexManager()
    val corePlaylistService = runtime.createPlaylistService(securityService, settingsService, mediaFileService, coverArtService)
    val mediaScannerService = runtime.createMediaScannerService(
        corePlaylistService,
        settingsService,
        searchIndex,
        mediaFileService,
        mediaFolderService,
        coverArtService,
    )
    libraryScanService = StandaloneLibraryScanService(
        standaloneDatabase.dataSource,
        mediaFolderService,
        mediaFileService,
        mediaScannerService,
    )
    val server = embeddedServer(Netty, host = host, port = port) {
        standaloneModule(
            authService,
            libraryScanService,
            playlistService,
            favoriteService,
            mediaFolderService,
            mediaCatalogService,
            playQueueService,
            bookmarkService,
        )
    }
    lifecycle.registerShutdown("ktor-server", AutoCloseable { server.stop(1_000, 5_000) })
    server.start(wait = true)
}

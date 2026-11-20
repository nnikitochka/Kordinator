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
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.security.LoginRateLimiter
import org.airsonic.player.service.StandaloneLibraryService
import org.airsonic.player.service.StandaloneLibraryScanService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandaloneAlbum
import org.airsonic.player.service.StandaloneArtist
import org.airsonic.player.service.StandaloneTrack
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.MediaFolderService
import java.nio.file.Path
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
    libraryService: StandaloneLibraryService? = null,
    libraryScanService: StandaloneLibraryScanService? = null,
    playlistService: StandalonePlaylistService? = null,
    favoriteService: StandaloneFavoriteService? = null,
    mediaFolderService: MediaFolderService? = null,
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
                    if (mediaFolderService != null) {
                        KordinatorMusicFolderAdminRoutes(mediaFolderService, jsonMapper).routes(this)
                    }
                    get("/search") {
                        if (libraryScanService == null) {
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
                        val (matches, total) = withContext(scanDispatcher) {
                            libraryScanService.tracks(null, query, limit, offset) to libraryScanService.countTracks(null, query)
                        }
                        call.respondJson(StandaloneSearchResponse(query, offset, total, matches.map {
                            TrackResponse(it.id, it.libraryId, it.relativePath, it.title, it.artist, it.album,
                                it.albumArtist, it.durationSeconds, it.sizeBytes, it.modifiedAt?.toString())
                        }))
                    }
                    get("/albums") {
                        if (libraryScanService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val requestedLibraryId = call.request.queryParameters["libraryId"]
                        val libraryId = requestedLibraryId?.toLongOrNull()
                        val limitValue = call.request.queryParameters["limit"]
                        val offsetValue = call.request.queryParameters["offset"]
                        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
                        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
                        if ((requestedLibraryId != null && (libraryId == null || libraryId < 0)) ||
                            limit == null || limit !in 1..100 || offset == null || offset < 0
                        ) {
                            call.respondJson(ApiError("invalid_request", "libraryId must be non-negative, limit between 1 and 100, and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val albums = withContext(scanDispatcher) { libraryScanService.albums(libraryId, limit, offset) }
                        call.respondJson(AlbumPage(albums.map { it.toResponse() }, albums.size, limit, offset))
                    }
                    get("/albums/{id}") {
                        val selector = call.parameters["id"]?.decodeCatalogId()
                        if (selector == null) {
                            call.respondJson(ApiError("invalid_request", "Album ID is invalid"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val service = libraryScanService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val tracks = withContext(scanDispatcher) { service.tracksForAlbum(selector.first, selector.second) }
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
                        if (libraryScanService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val requestedLibraryId = call.request.queryParameters["libraryId"]
                        val libraryId = requestedLibraryId?.toLongOrNull()
                        val limitValue = call.request.queryParameters["limit"]
                        val offsetValue = call.request.queryParameters["offset"]
                        val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
                        val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
                        if ((requestedLibraryId != null && (libraryId == null || libraryId < 0)) ||
                            limit == null || limit !in 1..100 || offset == null || offset < 0
                        ) {
                            call.respondJson(ApiError("invalid_request", "libraryId must be non-negative, limit between 1 and 100, and offset non-negative"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val artists = withContext(scanDispatcher) { libraryScanService.artists(libraryId, limit, offset) }
                        call.respondJson(ArtistPage(artists.map { it.toResponse() }, artists.size, limit, offset))
                    }
                    get("/artists/{id}") {
                        val selector = call.parameters["id"]?.decodeCatalogId()
                        if (selector == null) {
                            call.respondJson(ApiError("invalid_request", "Artist ID is invalid"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val service = libraryScanService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val tracks = withContext(scanDispatcher) { service.tracksForArtist(selector.first, selector.second) }
                        if (tracks.isEmpty()) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondJson(ArtistDetailsResponse(
                            catalogId(selector.first, selector.second), selector.second, tracks.map { it.toResponse() },
                        ))
                    }
                    get("/tracks") {
                    if (libraryScanService == null) {
                        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val requestedLibraryId = call.request.queryParameters["libraryId"]
                    val libraryId = requestedLibraryId?.toLongOrNull()
                    if (requestedLibraryId != null && libraryId == null) {
                        call.respondJson(ApiError("invalid_request", "libraryId must be a number"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val query = call.request.queryParameters["q"]
                    val (requestedTracks, total) = withContext(scanDispatcher) {
                        libraryScanService.tracks(libraryId, query, limit, offset) to libraryScanService.countTracks(libraryId, query)
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
                        if (libraryScanService == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId < 0) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val track = withContext(scanDispatcher) { libraryScanService.track(trackId) }
                        if (track == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondJson(TrackResponse(track.id, track.libraryId, track.relativePath, track.title,
                            track.artist, track.album, track.albumArtist, track.durationSeconds, track.sizeBytes,
                            track.modifiedAt?.toString()))
                    }
                    get("/tracks/{id}/stream") {
                    if (libraryScanService == null) {
                        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val trackId = call.parameters["id"]?.toLongOrNull()
                    if (trackId == null || trackId < 0) {
                        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val track = withContext(scanDispatcher) { libraryScanService.streamFile(trackId) }
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
                    if (libraryScanService == null) {
                        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val trackId = call.parameters["id"]?.toLongOrNull()
                    if (trackId == null || trackId < 0) {
                        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                        return@get
                    }
                    val artwork = withContext(scanDispatcher) { libraryScanService.artwork(trackId) }
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
                        val tracks = withContext(scanDispatcher) { service.list(user.id, limit, offset) }
                        call.respondJson(FavoriteTrackPage(tracks.map { it.toResponse() }, tracks.size, service.count(user.id), limit, offset))
                    }
                    put("/tracks/{id}/favorite") {
                        setFavorite(call, favoriteService, true)
                    }
                    delete("/tracks/{id}/favorite") {
                        setFavorite(call, favoriteService, false)
                    }
                    get("/playlists") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (playlistService == null) {
                            call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val playlists = withContext(scanDispatcher) { playlistService.list(user.id) }
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
                            val playlist = withContext(scanDispatcher) { playlistService.create(user.id, request.name, request.trackIds) }
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
                        val playlist = withContext(scanDispatcher) { service.get(user.id, playlistId) }
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
                            val playlist = withContext(scanDispatcher) { service.update(user.id, playlistId, request.name, request.trackIds) }
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
                    get("/admin/libraries") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val libraries = libraryService?.let { service ->
                            withContext(authDispatcher) { service.list() }
                                .map { LibraryResponse(it.id, it.name, it.rootPath, it.createdAt.toString()) }
                        }.orEmpty()
                        call.respondJson(libraries)
                    }
                    post("/admin/libraries") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@post
                        }
                        if (libraryService == null) {
                            call.respondJson(ApiError("unavailable", "Library storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@post
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), CreateLibraryRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected name and path"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        try {
                            val library = withContext(authDispatcher) {
                                libraryService.create(request.name, Path.of(request.path))
                            }
                            call.respondJson(LibraryResponse(library.id, library.name, library.rootPath, library.createdAt.toString()), HttpStatusCode.Created)
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_library", "Library name or path is invalid"), HttpStatusCode.BadRequest)
                        } catch (_: java.io.IOException) {
                            call.respondJson(ApiError("invalid_library", "Library path is not accessible"), HttpStatusCode.BadRequest)
                        } catch (failure: java.sql.SQLException) {
                            if (failure.sqlState?.startsWith("23") == true) {
                                call.respondJson(ApiError("library_exists", "A library with that name already exists"), HttpStatusCode.Conflict)
                            } else {
                                throw failure
                            }
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
data class ApiError(val code: String, val message: String)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class CreateUserRequest(val username: String, val password: String, val role: String = "USER")
data class SetUserEnabledRequest(val enabled: Boolean)
data class CreateLibraryRequest(val name: String, val path: String)
data class LibraryResponse(val id: Long, val name: String, val path: String, val createdAt: String)
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
    val updated = withContext(scanDispatcher) { service.set(user.id, trackId, favorite) }
    if (updated) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
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

fun main(args: Array<String>) {
    val configuration = RuntimeConfigurationLoader().load(args, System.getProperties(), System.getenv(), null, emptyMap())
    val host = configuration.getProperty("airsonic.http.host", "0.0.0.0")
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
    val libraryService = StandaloneLibraryService(standaloneDatabase.dataSource)
    val libraryScanService = StandaloneLibraryScanService(standaloneDatabase.dataSource)
    val playlistService = StandalonePlaylistService(standaloneDatabase.dataSource)
    val favoriteService = StandaloneFavoriteService(standaloneDatabase.dataSource)
    val mediaFolderService = runtime.createMediaFolderService()
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
    val server = embeddedServer(Netty, host = host, port = port) {
        standaloneModule(authService, libraryService, libraryScanService, playlistService, favoriteService, mediaFolderService)
    }
    lifecycle.registerShutdown("ktor-server", AutoCloseable { server.stop(1_000, 5_000) })
    server.start(wait = true)
}

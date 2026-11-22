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
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.jvm.javaio.toInputStream
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
import org.airsonic.player.service.CaptionService
import org.airsonic.player.service.StandalonePlaylistService
import org.airsonic.player.service.StandalonePlayQueueService
import org.airsonic.player.service.StandalonePlayQueue
import org.airsonic.player.service.StandaloneAlbum
import org.airsonic.player.service.StandaloneArtist
import org.airsonic.player.service.StandaloneTrack
import org.airsonic.player.service.StandaloneFavoriteService
import org.airsonic.player.service.StandaloneBookmarkService
import org.airsonic.player.service.StandaloneShareService
import org.airsonic.player.service.StandaloneShare
import org.airsonic.player.service.StandaloneAvatarService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.PathWatcherService
import org.airsonic.player.service.TaskSchedulingService
import org.airsonic.player.health.CompositeHealthContributor
import org.airsonic.player.health.Health
import org.airsonic.player.health.HealthContributor
import org.airsonic.player.health.HealthContributorRegistry
import org.airsonic.player.health.HealthIndicator
import org.airsonic.player.health.SimpleStatusAggregator
import org.airsonic.player.health.Status
import org.airsonic.player.health.StatusAggregator
import io.micrometer.core.instrument.MeterRegistry
import javax.cache.CacheManager
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.domain.TransferStatus
import org.airsonic.player.ajax.playing.NowPlayingInfo
import org.airsonic.player.service.ServerInfoService
import org.airsonic.player.service.StandaloneRatingService
import org.airsonic.player.service.StandaloneScrobbleService
import org.airsonic.player.service.LyricsService
import org.airsonic.player.service.SearchService
import org.airsonic.player.service.LastFmService
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.KtorCoreMediaCatalogService
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import java.nio.file.Path
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.security.MessageDigest

private val authDispatcher = Dispatchers.IO.limitedParallelism(4)
private val scanDispatcher = Dispatchers.IO.limitedParallelism(2)
private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024

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
    ratingService: StandaloneRatingService? = null,
    scrobbleService: StandaloneScrobbleService? = null,
    lyricsService: LyricsService? = null,
    randomSearchService: SearchService? = null,
    genreSeparators: String = ";",
    topSongsService: LastFmService? = null,
    mediaFileService: MediaFileService? = null,
    artistDao: ArtistDao? = null,
    mediaFileDao: MediaFileDao? = null,
    captionService: CaptionService? = null,
    shareService: StandaloneShareService? = null,
    avatarService: StandaloneAvatarService? = null,
    healthContributors: HealthContributorRegistry? = null,
    healthStatusAggregator: StatusAggregator? = null,
    pathWatcherService: PathWatcherService? = null,
    taskSchedulingService: TaskSchedulingService? = null,
    cacheManager: CacheManager? = null,
    meterRegistry: MeterRegistry? = null,
    statusService: StatusService? = null,
    securityService: SecurityService? = null,
    serverInfoService: ServerInfoService? = null,
    settingsService: SettingsService? = null,
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
        get("/api/v1/public/shares/{token}") {
            val service = shareService ?: return@get call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
            val token = call.parameters["token"] ?: return@get call.respond(HttpStatusCode.NotFound)
            val share = withContext(authDispatcher) { service.publicShare(token) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            val tracks = mediaCatalogService?.let { catalog ->
                withContext(scanDispatcher) { catalog.tracksByCoreIds(share.trackIds) }
            }.orEmpty()
            if (tracks.size != share.trackIds.size) return@get call.respond(HttpStatusCode.NotFound)
            call.respondJson(PublicShareResponse(share.description, share.expiresAt?.toString(), tracks.map {
                PublicShareTrackResponse(it.toTrackResponse(), "/api/v1/public/shares/$token/tracks/${it.id}/stream")
            }))
        }
        get("/api/v1/public/shares/{token}/tracks/{trackId}/stream") {
            respondPublicShareTrack(call, shareService, libraryScanService, mediaCatalogService, head = false)
        }
        head("/api/v1/public/shares/{token}/tracks/{trackId}/stream") {
            respondPublicShareTrack(call, shareService, libraryScanService, mediaCatalogService, head = true)
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
                    get("/tracks/random") {
                        val search = randomSearchService
                        val folders = mediaFolderService
                        val catalog = mediaCatalogService
                        if (search == null || folders == null || catalog == null) {
                            call.respondJson(ApiError("unavailable", "Random track search is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val parameters = call.request.queryParameters
                        val rawSize = parameters["size"]
                        val size = rawSize?.toIntOrNull() ?: if (rawSize == null) 10 else null
                        val rawFromYear = parameters["fromYear"]
                        val fromYear = rawFromYear?.toIntOrNull()
                        val rawToYear = parameters["toYear"]
                        val toYear = rawToYear?.toIntOrNull()
                        val rawLibraryId = parameters["libraryId"]
                        val libraryId = rawLibraryId?.toLongOrNull()
                        if (size == null || size !in 0..500 ||
                            rawFromYear != null && fromYear == null || rawToYear != null && toYear == null ||
                            rawLibraryId != null && (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong())) {
                            call.respondJson(ApiError("invalid_request", "Invalid size, year, or libraryId"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val libraryScope = user.catalogLibraryScope()
                        if (libraryId != null && libraryScope != null && libraryId !in libraryScope) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val allowedFolders = withContext(authDispatcher) {
                            folders.getAllMusicFolders(false, false).filter { folder ->
                                val id = folder.id?.toLong()
                                id != null && (libraryScope == null || id in libraryScope) && (libraryId == null || id == libraryId)
                            }
                        }
                        val result = withContext(scanDispatcher) {
                            search.getRandomSongs(RandomSearchCriteria(size, parameters["genre"], fromYear, toYear, allowedFolders))
                        }
                        val tracks = withContext(scanDispatcher) {
                            catalog.tracksByCoreIds(result.map { it.id.toLong() }, libraryScope)
                        }
                        call.respondJson(RandomTrackResponse(tracks.map { it.toResponse() }, tracks.size))
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
                        val total = withContext(scanDispatcher) {
                            mediaCatalogService.countAlbums(libraryId, accessibleLibraries)
                        }
                        call.respondJson(AlbumPage(albums.map { it.toResponse() }, albums.size, total, limit, offset))
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
                        val total = withContext(scanDispatcher) {
                            mediaCatalogService.countArtists(libraryId, accessibleLibraries)
                        }
                        call.respondJson(ArtistPage(artists.map { it.toResponse() }, artists.size, total, limit, offset))
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
                    get("/artists/{id}/top-tracks") {
                        val service = topSongsService
                        val folders = mediaFolderService
                        val catalog = mediaCatalogService
                        if (service == null || folders == null || catalog == null) {
                            call.respondJson(ApiError("unavailable", "Top track lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val artist = call.parameters["id"]?.decodeCatalogId()
                        if (artist == null || user.catalogLibraryScope()?.contains(artist.first) == false) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val requestedCount = call.request.queryParameters["count"]
                        val count = requestedCount?.toIntOrNull() ?: if (requestedCount == null) 50 else null
                        if (count == null || count !in 0..500) {
                            call.respondJson(ApiError("invalid_request", "count must be between 0 and 500"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val scope = user.catalogLibraryScope()
                        val allowedFolders = withContext(authDispatcher) {
                            folders.getAllMusicFolders(false, false).filter { folder ->
                                val id = folder.id?.toLong()
                                id != null && (scope == null || id in scope)
                            }
                        }
                        val recommendations = withContext(scanDispatcher) {
                            service.getTopSongs(artist.second, count, allowedFolders)
                        }
                        val tracks = withContext(scanDispatcher) {
                            catalog.tracksByCoreIds(recommendations.map { it.id.toLong() }, scope)
                        }
                        call.respondJson(TopTrackPage(artist.second, tracks.map { it.toResponse() }, tracks.size))
                    }
                    get("/artists/{id}/similar-tracks") {
                        val recommendations = topSongsService
                        val folders = mediaFolderService
                        val catalog = mediaCatalogService
                        val dao = artistDao
                        if (recommendations == null || folders == null || catalog == null || dao == null) {
                            call.respondJson(ApiError("unavailable", "Similar track lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val artist = call.parameters["id"]?.decodeCatalogId()
                        val scope = user.catalogLibraryScope()
                        if (artist == null || scope?.contains(artist.first) == false) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val requestedCount = call.request.queryParameters["count"]
                        val count = requestedCount?.toIntOrNull() ?: if (requestedCount == null) 50 else null
                        if (count == null || count !in 0..500) {
                            call.respondJson(ApiError("invalid_request", "count must be between 0 and 500"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        if (withContext(scanDispatcher) { catalog.tracksForArtist(artist.first, artist.second, scope) }.isEmpty()) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val allowedFolders = withContext(authDispatcher) {
                            folders.getAllMusicFolders(false, false).filter { folder ->
                                val id = folder.id?.toLong()
                                id != null && (scope == null || id in scope)
                            }
                        }
                        val coreArtist = withContext(scanDispatcher) { dao.getArtist(artist.second, allowedFolders) }
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                        val similar = withContext(scanDispatcher) {
                            recommendations.getSimilarSongs(coreArtist, count, allowedFolders)
                        }
                        val tracks = withContext(scanDispatcher) {
                            catalog.tracksByCoreIds(similar.map { it.id.toLong() }, scope)
                        }
                        call.respondJson(SimilarTrackPage(artist.second, tracks.map { it.toResponse() }, tracks.size))
                    }
                    get("/tracks/{id}/similar-tracks") {
                        val recommendations = topSongsService
                        val folders = mediaFolderService
                        val catalog = mediaCatalogService
                        val files = mediaFileService
                        if (recommendations == null || folders == null || catalog == null || files == null) {
                            call.respondJson(ApiError("unavailable", "Similar track lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId !in 0..Int.MAX_VALUE.toLong()) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative integer"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val scope = user.catalogLibraryScope()
                        if (catalog.track(trackId, scope) == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val requestedCount = call.request.queryParameters["count"]
                        val count = requestedCount?.toIntOrNull() ?: if (requestedCount == null) 50 else null
                        if (count == null || count !in 0..500) {
                            call.respondJson(ApiError("invalid_request", "count must be between 0 and 500"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val allowedFolders = withContext(authDispatcher) {
                            folders.getAllMusicFolders(false, false).filter { folder ->
                                val id = folder.id?.toLong()
                                id != null && (scope == null || id in scope)
                            }
                        }
                        val mediaFile = withContext(scanDispatcher) { files.getMediaFile(trackId.toInt()) }
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                        val similar = withContext(scanDispatcher) {
                            recommendations.getSimilarSongs(mediaFile, count, allowedFolders)
                        }
                        val tracks = withContext(scanDispatcher) {
                            catalog.tracksByCoreIds(similar.map { it.id.toLong() }, scope)
                        }
                        call.respondJson(SimilarTrackPage(null, tracks.map { it.toResponse() }, tracks.size))
                    }
                    get("/genres") {
                        val catalog = mediaCatalogService
                        if (catalog == null) {
                            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val genres = withContext(scanDispatcher) { catalog.genres(user.catalogLibraryScope(), genreSeparators) }
                        call.respondJson(GenreListResponse(genres.map {
                            GenreResponse(it.name, it.trackCount, it.albumCount)
                        }, genres.size))
                    }
                    get("/videos") {
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val dao = mediaFileDao
                        val foldersService = mediaFolderService
                        if (dao == null || foldersService == null) {
                            call.respondJson(ApiError("unavailable", "Video catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val rawLimit = call.request.queryParameters["limit"]
                        val limit = rawLimit?.toIntOrNull() ?: if (rawLimit == null) 50 else null
                        val rawOffset = call.request.queryParameters["offset"]
                        val offset = rawOffset?.toIntOrNull() ?: if (rawOffset == null) 0 else null
                        if (limit == null || limit !in 1..100 || offset == null || offset < 0) {
                            call.respondJson(
                                ApiError("invalid_request", "Limit must be between 1 and 100 and offset must be non-negative"),
                                HttpStatusCode.BadRequest,
                            )
                            return@get
                        }
                        val scope = user.catalogLibraryScope()
                        val folders = withContext(authDispatcher) {
                            foldersService.getAllMusicFolders(false, false).filter { folder ->
                                val id = folder.id?.toLong()
                                id != null && (scope == null || id in scope)
                            }
                        }
                        val page = withContext(scanDispatcher) { dao.getVideos(limit + 1, offset, folders) }
                        val hasMore = page.size > limit
                        call.respondJson(VideoPage(page.take(limit).map { it.toVideoResponse() }, limit, offset, hasMore))
                    }
                    if (captionService != null && mediaFileService != null && mediaFolderService != null) {
                        KordinatorVideoCaptionRoutes(captionService, mediaFileService, mediaFolderService).routes(this)
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
                    val genre = call.request.queryParameters["genre"]
                    val accessibleLibraries = call.catalogLibraryScope()
                    if (libraryId != null && accessibleLibraries?.contains(libraryId) == false) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val (requestedTracks, total) = withContext(scanDispatcher) {
                        mediaCatalogService.tracks(libraryId, query, limit, offset, accessibleLibraries, genre) to
                            mediaCatalogService.countTracks(libraryId, query, accessibleLibraries, genre)
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
                    get("/tracks/{id}/stream") { respondTrackFile(call, libraryScanService, mediaCatalogService, false, false) }
                    head("/tracks/{id}/stream") {
                        respondTrackFile(call, libraryScanService, mediaCatalogService, false, true)
                    }
                    get("/tracks/{id}/download") { respondTrackFile(call, libraryScanService, mediaCatalogService, true, false) }
                    head("/tracks/{id}/download") {
                        respondTrackFile(call, libraryScanService, mediaCatalogService, true, true)
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
                    val etag = "\"${MessageDigest.getInstance("SHA-256").digest(artwork.bytes).joinToString("") { "%02x".format(it) }}\""
                    call.setRepresentationValidators(etag, null)
                    if (call.isNotModified(etag, null)) {
                        call.respond(HttpStatusCode.NotModified)
                        return@get
                    }
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
                    if (playlistService != null && mediaCatalogService != null) {
                        KordinatorPlaylistImportRoutes(playlistService, mediaCatalogService).routes(this)
                    }
                    get("/shares") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val service = shareService ?: return@get call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val shares = withContext(authDispatcher) { service.list(actor.id) }
                        call.respondJson(shares.map { it.toResponse(null, emptyList()) })
                    }
                    post("/shares") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        val service = shareService ?: return@post call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val catalog = mediaCatalogService ?: return@post call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), CreateShareRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected trackIds, optional description and expiresAt"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val expiresAt = try {
                            request.expiresAt?.let(java.time.Instant::parse)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_expiration", "expiresAt must be an ISO-8601 timestamp"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        try {
                            val share = withContext(scanDispatcher) {
                                service.create(actor.id, request.trackIds, request.description, expiresAt, actor.catalogLibraryScope())
                            }
                            val tracks = withContext(scanDispatcher) { catalog.tracksByCoreIds(share.trackIds, actor.catalogLibraryScope()) }
                            call.respondJson(share.toResponse(share.token, tracks.map { it.toTrackResponse() }), HttpStatusCode.Created)
                        } catch (failure: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_share", failure.message ?: "Share request is invalid"), HttpStatusCode.BadRequest)
                        }
                    }
                    put("/shares/{id}") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val service = shareService ?: return@put call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
                            ?: return@put call.respondJson(ApiError("invalid_request", "Share ID must be non-negative"), HttpStatusCode.BadRequest)
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), UpdateShareRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected description and expiresAt"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val expiresAt = try {
                            request.expiresAt?.let(java.time.Instant::parse)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_expiration", "expiresAt must be an ISO-8601 timestamp"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        try {
                            val updated = withContext(authDispatcher) { service.update(actor.id, id, request.description, expiresAt) }
                            if (!updated) call.respond(HttpStatusCode.NotFound)
                            else call.respondJson(requireNotNull(withContext(authDispatcher) { service.get(actor.id, id) }).toResponse(null, emptyList()))
                        } catch (failure: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_share", failure.message ?: "Share request is invalid"), HttpStatusCode.BadRequest)
                        }
                    }
                    delete("/shares/{id}") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        val service = shareService ?: return@delete call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
                            ?: return@delete call.respondJson(ApiError("invalid_request", "Share ID must be non-negative"), HttpStatusCode.BadRequest)
                        val deleted = withContext(authDispatcher) { service.delete(actor.id, id) }
                        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
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
                    get("/playlists/{id}/export") {
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
                        if (playlist == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val fileName = playlist.name
                            .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_') it else '_' }
                            .joinToString("").trim('_').ifEmpty { "playlist" } + ".m3u8"
                        val content = buildString {
                            appendLine("#EXTM3U")
                            playlist.tracks.forEach { track ->
                                val label = listOfNotNull(track.artist?.takeIf(String::isNotBlank), track.title)
                                    .joinToString(" - ").replace('\r', ' ').replace('\n', ' ')
                                appendLine("#EXTINF:${track.durationSeconds},$label")
                                appendLine(track.relativePath.replace('\\', '/'))
                            }
                        }
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
                        call.respondText(content, ContentType.parse("application/vnd.apple.mpegurl"))
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
                    get("/admin/health") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val contributors = healthContributors
                            ?: return@get call.respondJson(ApiError("unavailable", "Health contributors are not configured"), HttpStatusCode.ServiceUnavailable)
                        val aggregator = healthStatusAggregator ?: SimpleStatusAggregator()
                        val components = withContext(Dispatchers.IO) {
                            contributors.toList().sortedBy { it.name }.map { it.toHealthComponent(aggregator) }
                        }
                        call.respondJson(AdminHealthResponse(aggregator.getAggregateStatus(components.map { Status(it.status) }.toSet()).code, components))
                    }
                    get("/admin/path-watchers") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val watchers = pathWatcherService
                            ?: return@get call.respondJson(ApiError("unavailable", "Path watcher service is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.respondJson(watchers.watchers.toSortedMap().map { (name, path) -> PathWatcherResponse(name, path) })
                    }
                    get("/admin/scheduled-tasks") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val tasks = taskSchedulingService
                            ?: return@get call.respondJson(ApiError("unavailable", "Task scheduler is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.respondJson(tasks.taskInfos.sortedBy { it.name }.map { task ->
                            val metadata = task.runMetadata
                            ScheduledTaskResponse(
                                task.name, task.created.toString(), task.scheduledBy,
                                metadata?.type?.name, metadata?.firstRun?.toString(), metadata?.lastRun?.toString(), metadata?.nextRun?.toString(),
                            )
                        })
                    }
                    get("/admin/cache-statistics") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val caches = cacheManager
                            ?: return@get call.respondJson(ApiError("unavailable", "Cache manager is not configured"), HttpStatusCode.ServiceUnavailable)
                        val meters = meterRegistry
                            ?: return@get call.respondJson(ApiError("unavailable", "Meter registry is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respondJson(caches.cacheNames.sorted().map { name ->
                            CacheStatisticsResponse(
                                name,
                                cacheMetric(meters, "cache.gets", name, "result", "hit"),
                                cacheMetric(meters, "cache.gets", name, "result", "miss"),
                                cacheMetric(meters, "cache.puts", name),
                                cacheMetric(meters, "cache.removals", name),
                            )
                        })
                    }
                    delete("/admin/cache-statistics/{name}") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@delete
                        }
                        val caches = cacheManager
                            ?: return@delete call.respondJson(ApiError("unavailable", "Cache manager is not configured"), HttpStatusCode.ServiceUnavailable)
                        val name = call.parameters["name"]
                            ?: return@delete call.respondJson(ApiError("missing_parameter", "Missing cache name"), HttpStatusCode.BadRequest)
                        val cache = caches.getCache<Any, Any>(name)
                            ?: return@delete call.respondJson(ApiError("not_found", "Cache not found"), HttpStatusCode.NotFound)
                        withContext(Dispatchers.IO) { cache.removeAll() }
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respond(HttpStatusCode.NoContent)
                    }
                    get("/admin/transfer-statistics") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val status = statusService
                            ?: return@get call.respondJson(ApiError("unavailable", "Transfer status service is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        val transfers = withContext(Dispatchers.IO) {
                            buildList {
                                status.getAllStreamStatuses().filter { it.millisSinceLastUpdate < 3_600_000L }
                                    .forEach { add(it.toTransferResponse("stream")) }
                                status.getAllDownloadStatuses().forEach { add(it.toTransferResponse("download")) }
                                status.getAllUploadStatuses().forEach { add(it.toTransferResponse("upload")) }
                            }
                        }
                        call.respondJson(transfers)
                    }
                    get("/admin/user-transfer-statistics") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val security = securityService
                            ?: return@get call.respondJson(ApiError("unavailable", "User statistics service is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        val users = withContext(Dispatchers.IO) {
                            security.allUsers.sortedBy { it.username }.map { user ->
                                UserTransferStatisticsResponse(user.username, user.bytesStreamed, user.bytesDownloaded, user.bytesUploaded)
                            }
                        }
                        call.respondJson(users)
                    }
                    get("/now-playing") {
                        call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val status = statusService
                            ?: return@get call.respondJson(ApiError("unavailable", "Playback status service is not configured"), HttpStatusCode.ServiceUnavailable)
                        val playing = withContext(Dispatchers.IO) {
                            (status.getActivePlays() + status.getInactivePlays()).mapNotNull { it.toNowPlayingResponse() }
                        }
                        call.respondJson(playing)
                    }
                    get("/admin/server-info") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val info = serverInfoService
                            ?: return@get call.respondJson(ApiError("unavailable", "Server information service is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respondJson(withContext(Dispatchers.IO) { info.snapshot("Kordinator") })
                    }
                    get("/admin/server-log") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val info = serverInfoService
                            ?: return@get call.respondJson(ApiError("unavailable", "Server information service is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respondJson(withContext(Dispatchers.IO) { info.log() })
                    }
                    get("/admin/getting-started") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val settings = settingsService
                            ?: return@get call.respondJson(ApiError("unavailable", "Settings service is not configured"), HttpStatusCode.ServiceUnavailable)
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respondJson(mapOf(
                            "runningAsRoot" to (System.getProperty("user.name") == "root"),
                            "enabled" to settings.isGettingStartedEnabled,
                        ))
                    }
                    delete("/admin/getting-started") {
                        val actor = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@delete
                        }
                        val settings = settingsService
                            ?: return@delete call.respondJson(ApiError("unavailable", "Settings service is not configured"), HttpStatusCode.ServiceUnavailable)
                        withContext(Dispatchers.IO) {
                            settings.isGettingStartedEnabled = false
                            settings.save()
                        }
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respond(HttpStatusCode.NoContent)
                    }
                    get("/me/avatar") {
                        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val service = avatarService ?: return@get call.respondJson(ApiError("unavailable", "Avatar storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val avatar = withContext(authDispatcher) { service.get(user.id) }
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                        call.respondBytes(avatar.bytes, ContentType.Image.PNG)
                    }
                    put("/me/avatar") {
                        val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val service = avatarService ?: return@put call.respondJson(ApiError("unavailable", "Avatar storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        if (contentLength != null && contentLength > MAX_AVATAR_BYTES + 65536L) {
                            call.respondJson(ApiError("avatar_too_large", "Avatar exceeds 5 MiB"), HttpStatusCode.PayloadTooLarge)
                            return@put
                        }
                        var uploaded: ByteArray? = null
                        var uploadFailure: String? = null
                        try {
                            if (call.request.headers[HttpHeaders.ContentType]?.startsWith("multipart/form-data", true) != true) {
                                throw IllegalArgumentException("Multipart upload required")
                            }
                            call.receiveMultipart(formFieldLimit = 1024L).forEachPart { part ->
                                try {
                                    when (part) {
                                        is PartData.FileItem -> {
                                            if (uploaded != null || part.name != "file") {
                                                uploadFailure = "Exactly one file field named file is required"
                                            } else {
                                                val bytes = withContext(Dispatchers.IO) {
                                                    part.provider().toInputStream().use { it.readNBytes(MAX_AVATAR_BYTES + 1) }
                                                }
                                                if (bytes.size > MAX_AVATAR_BYTES) uploadFailure = "Avatar exceeds 5 MiB"
                                                else uploaded = bytes
                                            }
                                        }
                                        else -> uploadFailure = "Avatar upload must contain a single file field"
                                    }
                                } finally {
                                    part.dispose()
                                }
                            }
                            if (uploadFailure != null) {
                                val tooLarge = uploadFailure!!.contains("5 MiB")
                                call.respondJson(
                                    ApiError(if (tooLarge) "avatar_too_large" else "invalid_upload", uploadFailure!!),
                                    if (tooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest,
                                )
                                return@put
                            }
                            val bytes = uploaded ?: throw IllegalArgumentException("A single file field is required")
                            val avatar = withContext(Dispatchers.IO) { service.set(user.id, bytes) }
                            call.respondJson(AvatarResponse(avatar.width, avatar.height, avatar.bytes.size), HttpStatusCode.Created)
                        } catch (failure: IllegalArgumentException) {
                            val tooLarge = failure.message?.contains("5 MiB") == true
                            call.respondJson(
                                ApiError(if (tooLarge) "avatar_too_large" else "invalid_avatar", failure.message ?: "Avatar is invalid"),
                                if (tooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest,
                            )
                        }
                    }
                    delete("/me/avatar") {
                        val user = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        val service = avatarService ?: return@delete call.respondJson(ApiError("unavailable", "Avatar storage is not configured"), HttpStatusCode.ServiceUnavailable)
                        val deleted = withContext(authDispatcher) { service.delete(user.id) }
                        if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
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
                    get("/tracks/{id}/rating") {
                        val service = ratingService
                        val catalog = mediaCatalogService
                        if (service == null || catalog == null) {
                            call.respondJson(ApiError("unavailable", "Rating storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId < 0) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        if (catalog.track(trackId, user.catalogLibraryScope()) == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }
                        val rating = withContext(authDispatcher) { service.get(user.id, trackId) }
                        call.respondJson(TrackRatingResponse(rating))
                    }
                    put("/tracks/{id}/rating") {
                        val service = ratingService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Rating storage is not configured"), HttpStatusCode.ServiceUnavailable)
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
                            jsonMapper.readValue(call.receiveText(), SetTrackRatingRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected rating from 0 to 5"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        val saved = try {
                            withContext(scanDispatcher) {
                                service.set(user.id, trackId, request.rating, user.catalogLibraryScope())
                            }
                        } catch (failure: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_request", failure.message ?: "Rating is invalid"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        if (saved) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
                    }
                    post("/tracks/{id}/scrobble") {
                        val service = scrobbleService
                        val catalog = mediaCatalogService
                        if (service == null || catalog == null) {
                            call.respondJson(ApiError("unavailable", "Scrobble processing is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@post
                        }
                        val user = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        val trackId = call.parameters["id"]?.toLongOrNull()
                        if (trackId == null || trackId < 0) {
                            call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val request = try {
                            jsonMapper.readValue(call.receiveText(), ScrobbleTrackRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected optional ISO-8601 time and submission flag"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val time = try {
                            request.time?.let(java.time.Instant::parse)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Time must be ISO-8601"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val accessible = withContext(scanDispatcher) { catalog.track(trackId, user.catalogLibraryScope()) }
                        if (accessible == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@post
                        }
                        val recorded = withContext(scanDispatcher) {
                            service.scrobble(user.username, trackId, time, request.submission)
                        }
                        if (recorded) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound)
                    }
                    get("/lyrics") {
                        val service = lyricsService
                        if (service == null) {
                            call.respondJson(ApiError("unavailable", "Lyrics lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@get
                        }
                        val artist = call.request.queryParameters["artist"]
                        val title = call.request.queryParameters["title"]
                        val result = withContext(authDispatcher) { service.getLyrics(artist, title) }
                        call.respondJson(
                            LyricsResponse(
                                result.artist(), result.title(), result.lyrics(),
                                if (call.request.queryParameters["includeLookupStatus"] == "true") result.tryLater() else null,
                            ),
                        )
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
                    get("/admin/users/{id}") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@get
                        }
                        val targetId = call.parameters["id"]?.toLongOrNull()
                        if (targetId == null || targetId < 0) {
                            call.respondJson(ApiError("invalid_request", "User ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@get
                        }
                        val user = withContext(authDispatcher) { authService.users().firstOrNull { it.id == targetId } }
                        val libraries = withContext(authDispatcher) { authService.allowedLibraries(targetId) }
                        if (user == null || libraries == null) call.respond(HttpStatusCode.NotFound)
                        else call.respondJson(AdminUserDetailsResponse(
                            AdminUserResponse(user.id, user.username, user.role, user.enabled),
                            libraries.sorted(),
                        ))
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
                    put("/admin/users/{id}/password") {
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
                            jsonMapper.readValue(call.receiveText(), ResetUserPasswordRequest::class.java)
                        } catch (_: Exception) {
                            call.respondJson(ApiError("invalid_request", "Expected password"), HttpStatusCode.BadRequest)
                            return@put
                        }
                        try {
                            val reset = withContext(authDispatcher) { authService.resetPassword(targetId, request.password) }
                            if (reset) call.respond(HttpStatusCode.NoContent) else {
                                call.respondJson(ApiError("user_not_found", "User does not exist"), HttpStatusCode.NotFound)
                            }
                        } catch (_: IllegalArgumentException) {
                            call.respondJson(ApiError("invalid_password", "Password must contain 12 to 1024 characters"), HttpStatusCode.BadRequest)
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
                    post("/admin/libraries/{id}/files") {
                        val actor = call.principal<LocalUserPrincipal>()
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        if (actor.role != "ADMIN") {
                            call.respondJson(ApiError("forbidden", "Administrator role required"), HttpStatusCode.Forbidden)
                            return@post
                        }
                        val scanService = libraryScanService
                        if (scanService == null) {
                            call.respondJson(ApiError("unavailable", "Library storage is not configured"), HttpStatusCode.ServiceUnavailable)
                            return@post
                        }
                        val libraryId = call.parameters["id"]?.toLongOrNull()
                        if (libraryId == null || libraryId !in 0..Int.MAX_VALUE.toLong()) {
                            call.respondJson(ApiError("invalid_request", "Library ID must be a non-negative number"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        val requestedPath = call.request.queryParameters["path"]?.trim()
                        if (requestedPath != null && requestedPath.isBlank()) {
                            call.respondJson(ApiError("invalid_request", "Upload path must not be blank"), HttpStatusCode.BadRequest)
                            return@post
                        }
                        try {
                            var uploaded: org.airsonic.player.service.StandaloneUploadedFile? = null
                            call.receiveMultipart(formFieldLimit = 1024L).forEachPart { part ->
                                try {
                                    if (part is PartData.FileItem) {
                                        require(uploaded == null && part.name == "file") { "Exactly one file field is required" }
                                        val filename = part.originalFileName?.replace('\\', '/')?.substringAfterLast('/')
                                        require(!filename.isNullOrBlank() && filename != "." && filename != ".." && filename.none(Char::isISOControl)) {
                                            "Invalid upload filename"
                                        }
                                        val destination = requestedPath ?: filename
                                        uploaded = withContext(Dispatchers.IO) {
                                            scanService.uploadFile(libraryId, destination, part.provider().toInputStream())
                                        }
                                    }
                                } finally {
                                    part.dispose()
                                }
                            }
                            val result = uploaded
                            if (result == null) {
                                call.respondJson(ApiError("invalid_request", "A single file field is required"), HttpStatusCode.BadRequest)
                            } else {
                                call.respondJson(
                                    UploadedLibraryFileResponse(libraryId, result.relativePath, result.sizeBytes),
                                    HttpStatusCode.Created,
                                )
                            }
                        } catch (failure: java.nio.file.FileAlreadyExistsException) {
                            call.respondJson(ApiError("file_exists", "The destination file already exists"), HttpStatusCode.Conflict)
                        } catch (failure: java.nio.file.AccessDeniedException) {
                            call.respondJson(ApiError("upload_denied", failure.message ?: "Upload path is not allowed"), HttpStatusCode.Forbidden)
                        } catch (failure: IllegalArgumentException) {
                            val tooLarge = failure.message?.contains("2 GiB") == true
                            call.respondJson(
                                ApiError(if (tooLarge) "upload_too_large" else "invalid_upload", failure.message ?: "Upload is invalid"),
                                if (tooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest,
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
data class AdminUserDetailsResponse(val user: AdminUserResponse, val libraryIds: List<Long>)
data class AdminHealthResponse(val status: String, val components: List<HealthComponentResponse>)
data class HealthComponentResponse(
    val name: String,
    val status: String,
    val details: Map<String, Any?> = emptyMap(),
    val components: List<HealthComponentResponse> = emptyList(),
)
data class PathWatcherResponse(val name: String, val path: String)
data class ScheduledTaskResponse(
    val name: String,
    val createdAt: String,
    val scheduledBy: String,
    val type: String?,
    val firstRun: String?,
    val lastRun: String?,
    val nextRun: String?,
)
data class CacheStatisticsResponse(val name: String, val hits: Double?, val misses: Double?, val puts: Double?, val removals: Double?)
data class TransferSampleResponse(val timestamp: Long, val bytesTransferred: Long)
data class TransferStatisticsResponse(
    val type: String,
    val username: String?,
    val playerType: String?,
    val playerName: String?,
    val path: String?,
    val bytesTransferred: Long,
    val history: List<TransferSampleResponse>,
)
data class UserTransferStatisticsResponse(val username: String, val streamed: Long, val downloaded: Long, val uploaded: Long)
data class NowPlayingResponse(
    val transferId: String?,
    val playerId: Int?,
    val trackId: Int?,
    val username: String?,
    val artist: String?,
    val title: String?,
    val minutesAgo: Long,
)
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
data class SetTrackRatingRequest(val rating: Int)
data class TrackRatingResponse(val rating: Int?)
data class ScrobbleTrackRequest(val time: String? = null, val submission: Boolean = true)
data class LyricsResponse(val artist: String?, val title: String?, val lyrics: String?, val tryLater: Boolean?)
data class RandomTrackResponse(val items: List<TrackResponse>, val count: Int)
data class GenreListResponse(val items: List<GenreResponse>, val count: Int)
data class GenreResponse(val name: String, val trackCount: Int, val albumCount: Int)
data class VideoPage(val items: List<VideoResponse>, val limit: Int, val offset: Int, val hasMore: Boolean)
data class VideoResponse(
    val id: Int,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSeconds: Int?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val format: String?,
)
data class TopTrackPage(val artist: String, val items: List<TrackResponse>, val count: Int)
data class SimilarTrackPage(val artist: String?, val items: List<TrackResponse>, val count: Int)
data class CreateUserRequest(val username: String, val password: String, val role: String = "USER")
data class ResetUserPasswordRequest(val password: String)
data class RenameUserRequest(val username: String)
data class SetUserEnabledRequest(val enabled: Boolean)
data class SetUserRoleRequest(val role: String)
data class SetUserLibrariesRequest(val libraryIds: Set<Long>)
data class CreateLibraryRequest(val name: String, val path: String)
data class UpdateLibraryRequest(val name: String)
data class LibraryResponse(val id: Long, val name: String, val path: String, val enabled: Boolean, val createdAt: String)
data class UploadedLibraryFileResponse(val libraryId: Long, val path: String, val sizeBytes: Long)
data class AvatarResponse(val width: Int, val height: Int, val sizeBytes: Int)
data class CreateShareRequest(val trackIds: List<Long> = emptyList(), val description: String? = null, val expiresAt: String? = null)
data class UpdateShareRequest(val description: String? = null, val expiresAt: String? = null)
data class ShareResponse(
    val id: Long,
    val trackIds: List<Long>,
    val description: String?,
    val createdAt: String,
    val expiresAt: String?,
    val visitCount: Long,
    val lastVisitedAt: String?,
    val token: String?,
    val tracks: List<TrackResponse>,
)
data class PublicShareResponse(val description: String?, val expiresAt: String?, val tracks: List<PublicShareTrackResponse>)
data class PublicShareTrackResponse(val track: TrackResponse, val streamUrl: String)
private fun MusicFolder.toLibraryResponse() = LibraryResponse(
    id = requireNotNull(id) { "Music folder has not been persisted" }.toLong(),
    name = name,
    path = path.toString(),
    enabled = isEnabled,
    createdAt = changed.toString(),
)
private fun org.airsonic.player.health.NamedContributor<HealthContributor>.toHealthComponent(
    aggregator: StatusAggregator,
): HealthComponentResponse = contributor.toHealthComponent(name, aggregator)

private fun HealthContributor.toHealthComponent(name: String, aggregator: StatusAggregator): HealthComponentResponse = when (this) {
    is HealthIndicator -> {
        val health = try {
            health()
        } catch (failure: Exception) {
            Health.down(failure).build()
        }
        HealthComponentResponse(name, health.status.code, health.details)
    }
    is CompositeHealthContributor -> {
        val children = toList().sortedBy { it.name }.map { it.toHealthComponent(aggregator) }
        HealthComponentResponse(name, aggregator.getAggregateStatus(children.map { Status(it.status) }.toSet()).code, components = children)
    }
    else -> HealthComponentResponse(name, Status.UNKNOWN.code)
}
private fun cacheMetric(meters: MeterRegistry, metric: String, cache: String, vararg tags: String): Double? {
    val found = meters.find(metric).tags("cache", cache).tags(*tags).meters()
    if (found.isEmpty()) return null
    val value = found.sumOf { meter -> meter.measure().sumOf { it.value } }
    return value.takeIf { it.isFinite() }
}
private fun TransferStatus.toTransferResponse(type: String) = TransferStatisticsResponse(
    type = type,
    username = player.username,
    playerType = player.type,
    playerName = player.name,
    path = mediaFile?.relativePath?.toString() ?: externalFile?.fileName?.toString(),
    bytesTransferred = bytesTransferred,
    history = history.map { TransferSampleResponse(it.timestamp, it.bytesTransferred) },
)
private fun NowPlayingInfo.toNowPlayingResponse(): NowPlayingResponse? {
    val play = fromPlayStatus() ?: return null
    val mediaFile = play.mediaFile ?: return null
    val player = play.player ?: return null
    return NowPlayingResponse(
        transferId = transferId?.toString(),
        playerId = player.id,
        trackId = mediaFile.id,
        username = player.username,
        artist = mediaFile.artist,
        title = mediaFile.title,
        minutesAgo = minutesAgo,
    )
}
private fun StandaloneShare.toResponse(token: String?, tracks: List<TrackResponse>) = ShareResponse(
    id, trackIds, description, createdAt.toString(), expiresAt?.toString(), visitCount, lastVisitedAt?.toString(), token, tracks,
)
private fun org.airsonic.player.service.StandaloneTrack.toTrackResponse() = TrackResponse(
    id, libraryId, relativePath, title, artist, album, albumArtist, durationSeconds, sizeBytes, modifiedAt?.toString(),
    discNumber, trackNumber, year, genre,
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
data class AlbumPage(val items: List<AlbumSummaryResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
data class ArtistPage(val items: List<ArtistSummaryResponse>, val count: Int, val total: Long, val limit: Int, val offset: Int)
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

private fun MediaFile.toVideoResponse() = VideoResponse(
    id = requireNotNull(id),
    title = name,
    artist = artist,
    album = albumName,
    durationSeconds = duration?.toInt(),
    sizeBytes = fileSize,
    width = width,
    height = height,
    format = format,
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

private suspend fun respondPublicShareTrack(
    call: ApplicationCall,
    shareService: StandaloneShareService?,
    scanService: StandaloneLibraryScanService?,
    catalogService: KtorCoreMediaCatalogService?,
    head: Boolean,
) {
    val service = shareService ?: return call.respondJson(ApiError("unavailable", "Share storage is not configured"), HttpStatusCode.ServiceUnavailable)
    val token = call.parameters["token"] ?: return call.respond(HttpStatusCode.NotFound)
    val trackId = call.parameters["trackId"]?.toLongOrNull()?.takeIf { it >= 0 }
        ?: return call.respondJson(ApiError("invalid_request", "Track ID must be non-negative"), HttpStatusCode.BadRequest)
    val share = withContext(authDispatcher) { service.publicShare(token) }
        ?: return call.respond(HttpStatusCode.NotFound)
    if (trackId !in share.trackIds) return call.respond(HttpStatusCode.NotFound)
    respondTrackFile(call, scanService, catalogService, false, head, trackId, publicAccess = true)
}

private suspend fun respondTrackFile(
    call: ApplicationCall,
    scanService: StandaloneLibraryScanService?,
    catalogService: KtorCoreMediaCatalogService?,
    download: Boolean,
    head: Boolean,
    trackIdOverride: Long? = null,
    publicAccess: Boolean = false,
) {
    if (scanService == null || catalogService == null) {
        call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val trackId = trackIdOverride ?: call.parameters["id"]?.toLongOrNull()
    if (trackId == null || trackId < 0) {
        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
        return
    }
    val catalogTrack = withContext(scanDispatcher) {
        catalogService.track(trackId, if (publicAccess) null else call.catalogLibraryScope())
    }
    if (catalogTrack == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val track = withContext(scanDispatcher) { scanService.streamFile(catalogTrack.libraryId, catalogTrack.relativePath) }
    if (track == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    if (download) {
        val fileName = track.path.fileName.toString()
            .map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("").ifBlank { "track" }
        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
    }
    val modifiedAt = runCatching { Files.getLastModifiedTime(track.path).toInstant() }.getOrNull()
    val etag = "W/\"$trackId-${track.sizeBytes}-${modifiedAt?.toEpochMilli() ?: 0}\""
    call.setRepresentationValidators(etag, modifiedAt)
    if (call.isNotModified(etag, modifiedAt)) {
        call.respond(HttpStatusCode.NotModified)
        return
    }
    call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    val rangeHeader = call.request.headers[HttpHeaders.Range]
    val range = if (rangeHeader == null) ByteRange(0, track.sizeBytes - 1) else parseByteRange(rangeHeader, track.sizeBytes)
    if (range == null) {
        call.response.headers.append(HttpHeaders.ContentRange, "bytes */${track.sizeBytes}")
        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        return
    }
    val partial = rangeHeader != null
    if (partial) call.response.headers.append(HttpHeaders.ContentRange, "bytes ${range.start}-${range.end}/${track.sizeBytes}")
    val length = if (track.sizeBytes == 0L && !partial) 0L else range.end - range.start + 1
    val contentType = ContentType.parse(track.contentType)
    val status = if (partial) HttpStatusCode.PartialContent else HttpStatusCode.OK
    if (head) {
        call.respond(object : OutgoingContent.NoContent() {
            override val status = status
            override val contentType = contentType
            override val contentLength = length
        })
        return
    }
    call.respondBytesWriter(contentType = contentType, status = status, contentLength = length) {
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

private fun ApplicationCall.setRepresentationValidators(etag: String, modifiedAt: Instant?) {
    response.headers.append(HttpHeaders.ETag, etag)
    modifiedAt?.let {
        response.headers.append(
            HttpHeaders.LastModified,
            DateTimeFormatter.RFC_1123_DATE_TIME.format(it.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).atZone(ZoneOffset.UTC)),
        )
    }
}

private fun ApplicationCall.isNotModified(etag: String, modifiedAt: Instant?): Boolean {
    val entityTags = request.headers[HttpHeaders.IfNoneMatch]
    if (entityTags != null) {
        val current = etag.removePrefix("W/").removeSurrounding("\"")
        return entityTags.split(',').any { candidate ->
            val normalized = candidate.trim()
            normalized == "*" || normalized.removePrefix("W/").removeSurrounding("\"") == current
        }
    }
    if (modifiedAt == null) return false
    val ifModifiedSince = request.headers[HttpHeaders.IfModifiedSince] ?: return false
    val since = runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSince))
    }.getOrNull() ?: return false
    return !modifiedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).isAfter(since)
}

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
    val shareService = StandaloneShareService(standaloneDatabase.dataSource, mediaCatalogService)
    val avatarService = StandaloneAvatarService(SettingsService.getAirsonicHome().resolve("avatars").resolve("accounts"))
    val healthContributors = runtime.createHealthContributorRegistry(runtime.createDataSource())
    val healthStatusAggregator = runtime.createStatusAggregator()
    val pathWatcherService = runtime.createPathWatcherService()
    val taskSchedulingService = runtime.createTaskSchedulingService()
    val cacheManager = runtime.createCacheManager()
    val meterRegistry = runtime.createMeterRegistry()
    val versionService = runtime.createVersionService()
    val serverInfoService = runtime.createServerInfoService(versionService, settingsService)
    val ratingService = StandaloneRatingService(standaloneDatabase.dataSource, mediaCatalogService)
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
    val statusService = runtime.createStatusService(
        mediaFileService,
        mediaFolderService,
        settingsService,
        runtime.createTaskSchedulingService(),
    )
    val scrobbleService = StandaloneScrobbleService(
        mediaFileService,
        statusService,
        runtime.createAudioScrobblerService(settingsService, securityService),
    )
    val lyricsService = runtime.createLyricsService()
    val searchIndex = runtime.createIndexManager()
    val randomSearchService = runtime.createSearchService(mediaFileService)
    val topSongsService = runtime.createLastFmService(legacyGraph.mediaFileDao, mediaFileService, legacyGraph.artistDao)
    val captionService = runtime.createCaptionService(
        mediaFileService,
        mediaFolderService,
        settingsService,
        runtime.createMetaDataParserFactory(),
        runtime.createJwtSecurityService(settingsService),
    )
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
            ratingService,
            scrobbleService,
            lyricsService,
            randomSearchService,
            settingsService.genreSeparators,
            topSongsService,
            mediaFileService,
            legacyGraph.artistDao,
            legacyGraph.mediaFileDao,
            captionService,
            shareService,
            avatarService,
            healthContributors,
            healthStatusAggregator,
            pathWatcherService,
            taskSchedulingService,
            cacheManager,
            meterRegistry,
            statusService,
            securityService,
            serverInfoService,
            settingsService,
        )
    }
    lifecycle.registerShutdown("ktor-server") { server.stop(1_000, 5_000) }
    server.start(wait = true)
}

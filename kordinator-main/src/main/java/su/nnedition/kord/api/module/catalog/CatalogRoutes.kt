package su.nnedition.kord.api.module.catalog

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.module.catalog.AvailableLibraryResponse
import su.nnedition.kord.api.module.jukebox.jukeboxRoutes
import su.nnedition.kord.api.module.library.libraryRoutes
import su.nnedition.kord.api.module.track.tag.trackTagRoutes
import su.nnedition.kord.api.module.video.bookmark.videoBookmarkRoutes
import su.nnedition.kord.api.module.video.caption.videoCaptionRoutes
import su.nnedition.kord.api.module.video.playback.videoPlaybackRoutes
import su.nnedition.kord.api.scanDispatcher
import su.nnedition.kord.api.support.catalogLibraryScope
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toVideoResponse

fun Route.catalogRoutes(
    context: ApiContext,
) {
    val monitor = context.application.monitor
    if (context.mediaFileService != null && context.mediaFolderService != null) {
        libraryRoutes(context.mediaFileService, context.mediaFolderService,
            currentUser = { applicationCall: ApplicationCall -> applicationCall.principal<LocalUserPrincipal>() },
            objectMapper = jsonMapper,
            apiPrefix = "",
        )
    }
    if (context.tagEditService != null && context.mediaFileService != null && context.mediaFolderService != null) {
        trackTagRoutes(context.tagEditService, context.mediaFileService, context.mediaFolderService)
    }
    if (context.videoPlaybackService != null && context.mediaFileService != null) {
        videoPlaybackRoutes(context.mediaFileService, context.videoPlaybackService)
        monitor.subscribe(ApplicationStopped) { context.videoPlaybackService.close() }
    }
    videoBookmarkRoutes(context.videoBookmarkService, jsonMapper, scanDispatcher)
    if (context.standaloneJukeboxService != null && context.mediaCatalogService != null && context.mediaFileService != null) {
        jukeboxRoutes(context.mediaCatalogService, context.mediaFileService, context.standaloneJukeboxService)
        monitor.subscribe(ApplicationStopped) { context.standaloneJukeboxService.close() }
    }
    get("/libraries") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val folders = context.mediaFolderService
            ?: return@get call.respondJson(ApiError("unavailable", "Library service is not configured"), HttpStatusCode.ServiceUnavailable)
        val scope = user.catalogLibraryScope()
        val libraries = withContext(authDispatcher) {
            folders.getAllMusicFolders(false, false)
            .filter { folder ->
                val id = folder.id?.toLong()
                id != null && (scope == null || id in scope)
            }
            .sortedWith(compareBy<MusicFolder> { it.name }.thenBy { it.id })
            .map { folder -> AvailableLibraryResponse(requireNotNull(folder.id).toLong(), folder.name) }
        }
        call.respondJson(libraries)
    }
    get("/genres") {
        val catalog = context.mediaCatalogService
        if (catalog == null) {
            call.respondJson(ApiError("unavailable", "Track catalog is not configured"), HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val genres = withContext(scanDispatcher) { catalog.genres(user.catalogLibraryScope(), context.genreSeparators) }
        call.respondJson(GenreListResponse(genres.map {
                    GenreResponse(it.name, it.trackCount, it.albumCount)
                }, genres.size))
    }
    get("/videos") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val dao = context.mediaFileDao
        val foldersService = context.mediaFolderService
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
    if (context.captionService != null && context.mediaFileService != null && context.mediaFolderService != null) {
        videoCaptionRoutes(context.captionService, context.mediaFileService, context.mediaFolderService)
    }
}

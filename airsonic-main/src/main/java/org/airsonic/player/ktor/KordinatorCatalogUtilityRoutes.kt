package org.airsonic.player.ktor

import io.ktor.server.routing.Route

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.coroutines.withContext
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.domain.MusicFolder
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher


internal class KordinatorCatalogUtilityRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
        val monitor = api.application.monitor
            if (mediaFileService != null && mediaFolderService != null) {
                KordinatorLibraryRoutes(mediaFileService, mediaFolderService).routes(
                    this@apply,
                    currentUser = { applicationCall: ApplicationCall -> applicationCall.principal<LocalUserPrincipal>() },
                    objectMapper = jsonMapper,
                    apiPrefix = "",
                )
            }
            if (tagEditService != null && mediaFileService != null && mediaFolderService != null) {
                KordinatorTrackTagRoutes(tagEditService, mediaFileService, mediaFolderService).routes(this@apply)
            }
            if (videoPlaybackService != null && mediaFileService != null) {
                StandaloneVideoPlaybackRoutes(mediaFileService, videoPlaybackService).routes(this@apply)
                monitor.subscribe(ApplicationStopped) { videoPlaybackService.close() }
            }
            StandaloneVideoBookmarkRoutes(videoBookmarkService, jsonMapper, scanDispatcher).routes(this@apply)
            if (standaloneJukeboxService != null && mediaCatalogService != null && mediaFileService != null) {
                StandaloneJukeboxRoutes(mediaCatalogService, mediaFileService, standaloneJukeboxService).routes(this@apply)
                monitor.subscribe(ApplicationStopped) { standaloneJukeboxService.close() }
            }
            get("/libraries") {
                val user = call.principal<LocalUserPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val folders = mediaFolderService
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
                KordinatorVideoCaptionRoutes(captionService, mediaFileService, mediaFolderService).routes(this@apply)
            }
        }
    }
}

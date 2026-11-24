package org.airsonic.player.ktor

import io.ktor.server.routing.Route

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.scanDispatcher


internal class KordinatorMetadataRoutes(private val api: ApiContext) {
    fun routes(route: Route) = route.apply {
        with(api) {
            get("/tracks/{id}/metadata") {
                val catalog = mediaCatalogService
                val files = mediaFileService
                val metadata = topSongsService
                if (catalog == null || files == null || metadata == null) {
                    call.respondJson(ApiError("unavailable", "Track metadata lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                val trackId = call.parameters["id"]?.toLongOrNull()
                if (trackId == null || trackId < 0) {
                    call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
                    return@get
                }
                val track = withContext(scanDispatcher) { catalog.track(trackId, call.catalogLibraryScope()) }
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val mediaFile = withContext(Dispatchers.IO) { files.getMediaFile(track.relativePath, track.libraryId.toInt()) }
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val (artistBio, albumNotes) = withContext(Dispatchers.IO) {
                    metadata.getArtistBio(mediaFile, java.util.Locale.getDefault()) to metadata.getAlbumNotes(mediaFile)
                }
                call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                call.respondJson(
                    TrackExternalMetadataResponse(
                        trackId,
                        artistBio?.let {
                            ArtistExternalMetadataResponse(
                                it.biography, it.musicBrainzId, it.lastFmUrl,
                                it.smallImageUrl, it.mediumImageUrl, it.largeImageUrl,
                            )
                        },
                        albumNotes?.let {
                            AlbumExternalMetadataResponse(
                                it.notes, it.musicBrainzId, it.lastFmUrl,
                                it.smallImageUrl, it.mediumImageUrl, it.largeImageUrl,
                            )
                        },
                    ),
                )
            }
            get("/artists/{id}/metadata") {
                val catalog = mediaCatalogService
                val files = mediaFileService
                val metadata = topSongsService
                if (catalog == null || files == null || metadata == null) {
                    call.respondJson(ApiError("unavailable", "Artist metadata lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                val artist = call.parameters["id"]?.decodeKordinatorCatalogId()
                if (artist == null) {
                    call.respondJson(ApiError("invalid_request", "Artist ID is invalid"), HttpStatusCode.BadRequest)
                    return@get
                }
                val tracks = withContext(scanDispatcher) {
                    catalog.tracksForArtist(artist.first, artist.second, call.catalogLibraryScope())
                }
                val firstTrack = tracks.firstOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
                val mediaFile = withContext(Dispatchers.IO) { files.getMediaFile(firstTrack.relativePath, firstTrack.libraryId.toInt()) }
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val bio = withContext(Dispatchers.IO) { metadata.getArtistBio(mediaFile, java.util.Locale.getDefault()) }
                call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                call.respondJson(bio?.let {
                    ArtistExternalMetadataResponse(it.biography, it.musicBrainzId, it.lastFmUrl,
                        it.smallImageUrl, it.mediumImageUrl, it.largeImageUrl)
                } ?: ArtistExternalMetadataResponse(null, null, null, null, null, null))
            }
            get("/albums/{id}/metadata") {
                val catalog = mediaCatalogService
                val files = mediaFileService
                val metadata = topSongsService
                if (catalog == null || files == null || metadata == null) {
                    call.respondJson(ApiError("unavailable", "Album metadata lookup is not configured"), HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                val album = call.parameters["id"]?.decodeKordinatorCatalogId()
                if (album == null) {
                    call.respondJson(ApiError("invalid_request", "Album ID is invalid"), HttpStatusCode.BadRequest)
                    return@get
                }
                val tracks = withContext(scanDispatcher) {
                    catalog.tracksForAlbum(album.first, album.second, call.catalogLibraryScope())
                }
                val firstTrack = tracks.firstOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
                val mediaFile = withContext(Dispatchers.IO) { files.getMediaFile(firstTrack.relativePath, firstTrack.libraryId.toInt()) }
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val notes = withContext(Dispatchers.IO) { metadata.getAlbumNotes(mediaFile) }
                call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                call.respondJson(notes?.let {
                    AlbumExternalMetadataResponse(it.notes, it.musicBrainzId, it.lastFmUrl,
                        it.smallImageUrl, it.mediumImageUrl, it.largeImageUrl)
                } ?: AlbumExternalMetadataResponse(null, null, null, null, null, null))
            }
        }
    }
}

package su.nnedition.kord.api.support

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.micrometer.core.instrument.MeterRegistry
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.withContext
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.PlayStatus
import su.nnedition.kord.domain.TransferStatus
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import su.nnedition.kord.service.library.scan.StandaloneAlbum
import su.nnedition.kord.service.library.scan.StandaloneArtist
import su.nnedition.kord.service.library.scan.StandaloneLibraryScanService
import su.nnedition.kord.service.StandalonePlaylistService
import su.nnedition.kord.service.StandaloneShare
import su.nnedition.kord.service.library.scan.StandaloneTrack
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.module.admin.status.NowPlayingResponse
import su.nnedition.kord.api.module.admin.status.TransferSampleResponse
import su.nnedition.kord.api.module.admin.status.TransferStatisticsResponse
import su.nnedition.kord.api.module.album.AlbumSummaryResponse
import su.nnedition.kord.api.module.artist.ArtistSummaryResponse
import su.nnedition.kord.api.module.catalog.VideoResponse
import su.nnedition.kord.api.module.share.ShareResponse
import su.nnedition.kord.api.module.track.TrackResponse
import su.nnedition.kord.api.scanDispatcher

internal fun cacheMetric(meters: MeterRegistry, metric: String, cache: String, vararg tags: String): Double? {
    val found = meters.find(metric).tags("cache", cache).tags(*tags).meters()
    if (found.isEmpty()) return null
    val value = found.sumOf { meter -> meter.measure().sumOf { it.value } }
    return value.takeIf { it.isFinite() }
}
internal fun TransferStatus.toTransferResponse(type: String): TransferStatisticsResponse {
    val transferPlayer = player!!
    return TransferStatisticsResponse(
        type = type,
        username = transferPlayer.username,
        playerType = transferPlayer.type,
        playerName = transferPlayer.name,
        path = mediaFile?.relativePath?.toString() ?: externalFile?.fileName?.toString(),
        bytesTransferred = bytesTransferred,
        history = history.map { TransferSampleResponse(it.timestamp, it.bytesTransferred) },
    )
}
internal fun PlayStatus.toNowPlayingResponse(): NowPlayingResponse? {
    val mediaFile = mediaFile ?: return null
    val player = player ?: return null
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
internal fun StandaloneShare.toResponse(token: String?, tracks: List<TrackResponse>) = ShareResponse(
    id,
    trackIds,
    description,
    createdAt.toString(),
    expiresAt?.toString(),
    visitCount,
    lastVisitedAt?.toString(),
    token,
    tracks,
)
internal fun StandaloneTrack.toCatalogTrackResponse() = TrackResponse(
    id, libraryId, relativePath, title, artist, album, albumArtist, durationSeconds, sizeBytes, modifiedAt?.toString(),
    discNumber, trackNumber, year, genre, releaseDate?.toString(),
)

internal fun StandaloneAlbum.toResponse() = AlbumSummaryResponse(
    kordinatorCatalogId(libraryId, name), libraryId, name, artist, year, genre, trackCount, durationSeconds, releaseDate?.toString(),
)

internal fun StandaloneArtist.toResponse() = ArtistSummaryResponse(
    kordinatorCatalogId(libraryId, name), libraryId, name, albumCount, trackCount,
)

internal fun StandaloneTrack.toResponse() = TrackResponse(
    id, libraryId, relativePath, title, artist, album, albumArtist, durationSeconds, sizeBytes, modifiedAt?.toString(),
    discNumber, trackNumber, year, genre, releaseDate?.toString(),
)

internal fun MediaFile.toVideoResponse() = VideoResponse(
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

internal fun kordinatorCatalogId(libraryId: Long, name: String): String =
    "$libraryId.${Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))}"

internal fun String.decodeKordinatorCatalogId(): Pair<Long, String>? {
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

internal fun LocalUserPrincipal.catalogLibraryScope(): Set<Long>? =
    if (role == "ADMIN") null else allowedLibraryIds

internal fun ApplicationCall.catalogLibraryScope(): Set<Long>? {
    val user = principal<LocalUserPrincipal>() ?: return emptySet()
    return user.catalogLibraryScope()
}

internal suspend fun respondTrackArtwork(
    call: ApplicationCall,
    catalog: KtorCoreMediaCatalogService?,
    scan: StandaloneLibraryScanService?,
    head: Boolean,
) {
    if (catalog == null || scan == null) {
        call.respondJson(ApiError("unavailable", "Track artwork is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val trackId = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
    if (trackId == null) {
        call.respondJson(ApiError("invalid_request", "Track ID must be a non-negative number"), HttpStatusCode.BadRequest)
        return
    }
    val track = withContext(scanDispatcher) { catalog.track(trackId, call.catalogLibraryScope()) }
        ?: return call.respond(HttpStatusCode.NotFound)
    val artwork = withContext(scanDispatcher) { scan.artwork(track.libraryId, track.relativePath) }
        ?: return call.respond(HttpStatusCode.NotFound)
    call.respondArtwork(artwork.bytes, artwork.contentType, head)
}

internal suspend fun respondCatalogEntityArtwork(
    call: ApplicationCall,
    catalog: KtorCoreMediaCatalogService?,
    scan: StandaloneLibraryScanService?,
    artist: Boolean,
    head: Boolean,
) {
    if (catalog == null || scan == null) {
        call.respondJson(ApiError("unavailable", "Catalog artwork is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val selector = call.parameters["id"]?.decodeKordinatorCatalogId()
    if (selector == null) {
        call.respondJson(ApiError("invalid_request", "Catalog ID is invalid"), HttpStatusCode.BadRequest)
        return
    }
    val tracks = withContext(scanDispatcher) {
        if (artist) catalog.tracksForArtist(selector.first, selector.second, call.catalogLibraryScope())
        else catalog.tracksForAlbum(selector.first, selector.second, call.catalogLibraryScope())
    }
    val track = tracks.firstOrNull()
    if (track == null) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val artwork = withContext(scanDispatcher) { scan.artwork(track.libraryId, track.relativePath) }
    if (artwork == null) call.respond(HttpStatusCode.NotFound)
    else call.respondArtwork(artwork.bytes, artwork.contentType, head)
}

internal suspend fun respondPlaylistArtwork(
    call: ApplicationCall,
    playlists: StandalonePlaylistService?,
    scan: StandaloneLibraryScanService?,
    head: Boolean,
) {
    val actor = call.principal<LocalUserPrincipal>()
        ?: return call.respond(HttpStatusCode.Unauthorized)
    val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it >= 0 }
        ?: return call.respondJson(ApiError("invalid_request", "Playlist ID must be a non-negative number"), HttpStatusCode.BadRequest)
    if (playlists == null) {
        call.respondJson(ApiError("unavailable", "Playlist storage is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val playlist = withContext(scanDispatcher) { playlists.get(actor.id, id, actor.catalogLibraryScope()) }
        ?: return call.respond(HttpStatusCode.NotFound)
    val track = playlist.tracks.firstOrNull() ?: return call.respond(HttpStatusCode.NotFound)
    if (scan == null) {
        call.respondJson(ApiError("unavailable", "Library artwork is not configured"), HttpStatusCode.ServiceUnavailable)
        return
    }
    val artwork = withContext(scanDispatcher) { scan.artwork(track.libraryId, track.relativePath) }
        ?: return call.respond(HttpStatusCode.NotFound)
    call.respondArtwork(artwork.bytes, artwork.contentType, head)
}

internal suspend fun ApplicationCall.respondArtwork(bytes: ByteArray, contentType: String, head: Boolean) {
    response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
    val etag = "\"${MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }}\""
    setRepresentationValidators(etag, null)
    if (isNotModified(etag, null)) {
        respond(HttpStatusCode.NotModified)
        return
    }
    if (head) {
        respond(object : OutgoingContent.NoContent() {
            override val contentType = ContentType.parse(contentType)
        })
    } else {
        respondBytes(bytes, ContentType.parse(contentType))
    }
}

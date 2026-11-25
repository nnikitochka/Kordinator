package su.nnedition.kord.api.module.playlist

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.nio.file.Paths
import kotlin.collections.orEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.nnedition.kord.security.LocalUserPrincipal
import su.nnedition.kord.service.KtorCoreMediaCatalogService
import su.nnedition.kord.service.StandalonePlaylistService
import su.nnedition.kord.api.ApiError

/** Imports an M3U playlist using only tracks visible to the authenticated account. */
fun Route.playlistImportRoutes(
    playlists: StandalonePlaylistService,
    catalog: KtorCoreMediaCatalogService,
) {
    val json = jacksonObjectMapper()

    suspend fun receiveFile(call: ApplicationCall): Pair<String, ByteArray> {
        require(call.request.headers["Content-Type"]?.startsWith("multipart/form-data", true) == true) {
            "Multipart M3U upload required"
        }
        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
        require(contentLength == null || contentLength <= MAX_BYTES + 65536L) { "Playlist exceeds 5 MiB" }
        var fileName: String? = null
        var content: ByteArray? = null
        call.receiveMultipart(formFieldLimit = MAX_BYTES + 65536L).forEachPart { part ->
            try {
                if (part is PartData.FileItem) {
                    require(content == null && part.name == "file") { "Exactly one file field is required" }
                    val bytes = withContext(Dispatchers.IO) {
                        part.provider().toInputStream().use { it.readNBytes(MAX_BYTES + 1) }
                    }
                    require(bytes.size <= MAX_BYTES) { "Playlist exceeds 5 MiB" }
                    fileName = part.originalFileName?.replace('\\', '/')?.substringAfterLast('/')
                    content = bytes
                }
            } finally {
                part.dispose()
            }
        }
        val bytes = content ?: throw IllegalArgumentException("Missing playlist file")
        require(bytes.isNotEmpty()) { "Playlist file is empty" }
        return requireNotNull(fileName) to bytes
    }

    fun parseM3u(bytes: ByteArray): List<String> {
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val paths = text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        require(paths.isNotEmpty()) { "Playlist contains no tracks" }
        require(paths.size <= MAX_TRACKS) { "Playlist contains too many tracks" }
        return paths.map { rawPath ->
            val path = rawPath.replace('\\', '/')
            require(!path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path)) { "Absolute paths are not allowed" }
            val normalized = Paths.get(path).normalize().toString().replace('\\', '/')
            require(normalized.isNotBlank() && normalized != "." && normalized != ".." && !normalized.startsWith("../")) {
                "Playlist contains an invalid relative path"
            }
            normalized
        }
    }

    suspend fun respondError(call: ApplicationCall, code: String, message: String, status: HttpStatusCode) {
        call.response.headers.append("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(ApiError(code, message)), ContentType.Application.Json, status)
    }

    fun LocalUserPrincipal.libraryScope(): Set<Long>? = if (role == "ADMIN") null else allowedLibraryIds

    suspend fun import(call: ApplicationCall) {
        val user = call.principal<LocalUserPrincipal>()
                ?: return call.respondText("", status = HttpStatusCode.Unauthorized)
        val (fileName, bytes) = try {
            receiveFile(call)
        } catch (failure: IllegalArgumentException) {
            return respondError(call, "invalid_import", failure.message ?: "Invalid playlist file", HttpStatusCode.BadRequest)
        }
        val paths = try {
            parseM3u(bytes)
        } catch (failure: IllegalArgumentException) {
            return respondError(call, "invalid_import", failure.message ?: "Invalid playlist file", HttpStatusCode.BadRequest)
        }
        val title = fileName.substringBeforeLast('.', fileName).trim()
        if (title.isBlank()) return respondError(call, "invalid_import", "Playlist filename is required", HttpStatusCode.BadRequest)
        val trackIds = try {
            withContext(Dispatchers.IO) {
                val byPath = catalog.tracksByRelativePaths(paths.toSet(), user.libraryScope())
                paths.map { path ->
                    val matches = byPath[path].orEmpty()
                    require(matches.size == 1) { "Playlist contains a missing or ambiguous track: $path" }
                    matches.single().id
                }
            }
        } catch (failure: IllegalArgumentException) {
            return respondError(call, "invalid_import", failure.message ?: "Playlist tracks are invalid", HttpStatusCode.BadRequest)
        }
        val playlist = try {
            withContext(Dispatchers.IO) { playlists.create(user.id, title, trackIds, user.libraryScope()) }
        } catch (failure: IllegalArgumentException) {
            return respondError(call, "invalid_import", failure.message ?: "Playlist is invalid", HttpStatusCode.BadRequest)
        }
        val response = PlaylistResponse(
            playlist.id,
            playlist.name,
            playlist.createdAt.toString(),
            playlist.updatedAt.toString(),
            playlist.visibility,
            playlist.description,
            playlist.tracks.map { it.id },
            playlist.tracks.map { PlaylistTrackResponse(it.id, it.title, it.artist, it.album, it.durationSeconds) },
        )
        call.response.headers.append("Cache-Control", "no-store")
        call.respondText(json.writeValueAsString(response), ContentType.Application.Json, HttpStatusCode.Created)
    }

    post("/playlists/import") { import(call) }
}

const val MAX_BYTES = 5 * 1024 * 1024
const val MAX_TRACKS = 5_000

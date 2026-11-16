package org.airsonic.player.controller.rest.shares

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.io.MoreFiles
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.service.VideoPlaybackService
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Share
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.User
import org.airsonic.player.service.JWTSecurityService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.ShareService
import org.airsonic.player.service.metadata.MetaDataParserFactory
import org.airsonic.player.util.StringUtil
import java.nio.file.Files
import java.time.Instant

/** Public website data; authentication is scoped to this share's signed URL. */
class PublicShareController(
    private val shares: ShareService,
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val jwt: JWTSecurityService,
    private val parsers: MetaDataParserFactory,
    private val json: ObjectMapper,
) {
    data class Request(val name: String, val player: Player, val baseUrl: String, val expiration: Instant?)

    fun dataUrl(name: String, baseUrl: String, expiration: Instant?): String = baseUrl + jwt.addJWTToken(
        User.USERNAME_ANONYMOUS, "ext/rest/share/$name", expiration
    )

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            get("/ext/rest/share/{name}") { respond(call, request(call), false) }
            head("/ext/rest/share/{name}") { respond(call, request(call), true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, head: Boolean) {
        call.response.header("Cache-Control", "no-store")
        val body = withContext(Dispatchers.IO) { load(request, !head) }
        call.respondText(json.writeValueAsString(body), ContentType.Application.Json)
    }

    private fun load(request: Request, countVisit: Boolean): Map<String, Any?> {
        val share = shares.getShareByName(request.name)
        val unavailable = mapOf("available" to false, "media" to emptyList<Any>())
        if (share == null || share.expires?.isBefore(Instant.now()) == true) return unavailable
        val expiration = listOfNotNull(request.expiration, share.expires).minOrNull()
        val media = selectedFiles(share, request.player).map { file -> media(file, request, expiration) }
        if (countVisit) {
            // HEAD and rendering the HTML shell must not register visits.
            share.lastVisited = Instant.now()
            share.visitCount += 1
            shares.updateShare(share)
        }
        return mapOf("available" to media.isNotEmpty(), "description" to share.description,
            "username" to share.username, "videoPresent" to media.any { it["video"] == true }, "media" to media)
    }

    // Social preview crawlers do not execute the script that loads the player data.
    fun preview(request: Request): Map<String, String>? {
        val share = shares.getShareByName(request.name) ?: return null
        if (share.expires?.isBefore(Instant.now()) == true) return null
        val file = selectedFiles(share, request.player).firstOrNull() ?: return null
        val expiration = listOfNotNull(request.expiration, share.expires).minOrNull()
        val imagePath = "ext/coverArt.view?id=${file.id}&size=500"
        val image = request.baseUrl + jwt.addJWTToken(User.USERNAME_GUEST, imagePath, expiration)
        return mapOf("title" to "${file.artist.orEmpty()} — ${file.albumName.orEmpty()}", "image" to image)
    }

    private fun selectedFiles(share: Share, player: Player): List<MediaFile> {
        val allowed = folders.getMusicFoldersForUser(player.username)
        return shares.getSharedFiles(share.id, allowed).flatMap { file ->
            if (!exists(file)) emptyList()
            else if (file.isDirectory) mediaFiles.getChildrenOf(file, true, false, true) else listOf(file)
        }.filter { file -> allowed.any { it.id == file.folderId } && exists(file) }
    }

    private fun exists(file: MediaFile): Boolean {
        val folder = folders.getMusicFolderById(file.folderId) ?: return false
        return Files.exists(file.getFullPath(folder.path))
    }

    private fun media(file: MediaFile, request: Request, expiration: Instant?): Map<String, Any?> {
        fun url(path: String, vararg parameters: Pair<String, Any?>): String {
            val relativeUrl = buildString {
                append(path).append("?id=").append(file.id)
                parameters.forEach { (name, value) ->
                    when (value) {
                        is Iterable<*> -> value.forEach { append('&').append(name).append('=').append(it) }
                        else -> append('&').append(name).append('=').append(value)
                    }
                }
            }
            return request.baseUrl + jwt.addJWTToken(User.USERNAME_GUEST, relativeUrl, expiration)
        }
        val direct = !file.isVideo || streamable(file)
        val stream = if (direct) url("ext/stream", "player" to request.player.id, "format" to "raw")
            else url("ext/hls/hls.m3u8", "player" to request.player.id, "maxBitRate" to VideoPlaybackService.BIT_RATES)
        return mapOf("id" to file.id, "title" to file.title, "artist" to file.artist, "album" to file.albumName,
            "video" to file.isVideo, "streamable" to direct,
            "contentType" to if (direct) StringUtil.getMimeType(MoreFiles.getFileExtension(file.relativePath)) else "application/x-mpegurl",
            "streamUrl" to stream, "coverArtUrl" to url("ext/coverArt.view", "size" to 500),
            "captionsUrl" to url("ext/rest/captions/list"))
    }

    private fun streamable(file: MediaFile): Boolean {
        if (file.format?.lowercase() !in setOf("mp4", "m4v")) return false
        val folder = folders.getMusicFolderById(file.folderId) ?: return false
        val path = file.getFullPath(folder.path)
        val metadata = parsers.getParser(path)?.getMetaData(path) ?: return true
        return metadata.videoTracks.firstOrNull()?.isStreamable != false &&
            metadata.audioTracks.firstOrNull()?.isStreamable != false
    }

    companion object {
        const val PAGE_PATH = "/ext/share"
        const val DATA_PATH = "/ext/rest/share"
    }
}

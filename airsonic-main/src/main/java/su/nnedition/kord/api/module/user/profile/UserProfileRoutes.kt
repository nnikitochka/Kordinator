package su.nnedition.kord.api.module.user.profile

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.net.URI
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.domain.AlbumListType
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.security.LocalUserPrincipal
import su.nnedition.kord.api.ApiContext
import su.nnedition.kord.api.ApiError
import su.nnedition.kord.api.MAX_AVATAR_BYTES
import su.nnedition.kord.api.authDispatcher
import su.nnedition.kord.api.module.user.profile.AvatarResponse
import su.nnedition.kord.api.support.MAX_PASSWORD_LENGTH
import su.nnedition.kord.api.support.jsonMapper
import su.nnedition.kord.api.support.respondJson
import su.nnedition.kord.api.support.toUserSettingsResponse

fun Route.userProfileRoutes(
    context: ApiContext,
) {
    val authService = requireNotNull(context.authService)
    get("/me") {
        val user = call.principal<LocalUserPrincipal>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respondJson(CurrentUserResponse(user.id, user.username, user.role))
    }
    get("/me/settings") {
        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.settingsService
            ?: return@get call.respondJson(ApiError("unavailable", "User settings are not configured"), HttpStatusCode.ServiceUnavailable)
        call.respondJson(withContext(Dispatchers.IO) { service.getUserSettings(user.username).toUserSettingsResponse() })
    }
    patch("/me/settings") {
        val user = call.principal<LocalUserPrincipal>() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val service = context.settingsService
            ?: return@patch call.respondJson(ApiError("unavailable", "User settings are not configured"), HttpStatusCode.ServiceUnavailable)
        val request = try {
            jsonMapper.readValue(call.receiveText(), UpdateUserSettingsRequest::class.java)
        } catch (_: Exception) {
            call.respondJson(ApiError("invalid_request", "Expected a JSON object with user setting fields"), HttpStatusCode.BadRequest)
            return@patch
        }
        try {
            val availableFields = request.hasChanges()
            require(availableFields) { "At least one setting must be provided" }
            request.themeId?.let { value ->
                require(value.isEmpty() || service.availableThemes.any { it.id == value }) { "Unknown theme" }
            }
            val parsedLocale = request.locale?.let { value ->
                if (value.isEmpty()) null else Locale.forLanguageTag(value).also {
                    require(it.language.isNotEmpty() && it.toLanguageTag().equals(value, true)) { "Unknown locale" }
                    require(service.availableLocales.any { option -> option.toLanguageTag().equals(value, true) }) { "Unknown locale" }
                }
            }
            val albumList = request.defaultAlbumList?.let {
                AlbumListType.fromId(it) ?: throw IllegalArgumentException("Unknown album list")
            }
            val transcode = request.transcodeScheme?.let {
                TranscodeScheme.entries.firstOrNull { option -> option.name == it }
                    ?: throw IllegalArgumentException("Unknown transcode scheme")
            }
            request.listenBrainzUrl?.takeIf { it.isNotBlank() }?.let {
                val uri = URI.create(it)
                require(uri.scheme in setOf("http", "https") && uri.host != null) { "Invalid ListenBrainz URL" }
            }
            request.paginationSizeFiles?.let { require(it == -1 || it > 0) { "Invalid files page size" } }
            request.paginationSizeFolders?.let { require(it == -1 || it > 0) { "Invalid folders page size" } }
            request.paginationSizePlaylist?.let { require(it == -1 || it > 0) { "Invalid playlist page size" } }
            request.paginationSizePlayqueue?.let { require(it == -1 || it > 0) { "Invalid play queue page size" } }
            request.paginationSizeBookmarks?.let { require(it == -1 || it > 0) { "Invalid bookmarks page size" } }
            request.audioBookmarkFrequency?.let { require(it > 0) { "Invalid audio bookmark frequency" } }
            request.videoBookmarkFrequency?.let { require(it > 0) { "Invalid video bookmark frequency" } }
            request.searchCount?.let { require(it > 0) { "Invalid search count" } }
            request.listenBrainzUrl?.let { require(it.isBlank() || it == it.trim()) { "ListenBrainz URL cannot have surrounding whitespace" } }

            val updated = withContext(Dispatchers.IO) {
                val value = service.getUserSettings(user.username)
                request.themeId?.let { value.themeId = it.ifEmpty { null } }
                if (request.locale != null) value.locale = parsedLocale
                if (albumList != null) value.defaultAlbumList = albumList
                if (transcode != null) value.transcodeScheme = transcode
                request.showNowPlayingEnabled?.let { value.showNowPlayingEnabled = it }
                request.showArtistInfoEnabled?.let { value.showArtistInfoEnabled = it }
                request.finalVersionNotificationEnabled?.let { value.finalVersionNotificationEnabled = it }
                request.betaVersionNotificationEnabled?.let { value.betaVersionNotificationEnabled = it }
                request.songNotificationEnabled?.let { value.songNotificationEnabled = it }
                request.keyboardShortcutsEnabled?.let { value.keyboardShortcutsEnabled = it }
                request.autoHidePlayQueue?.let { value.autoHidePlayQueue = it }
                request.showSideBar?.let { value.showSideBar = it }
                request.viewAsList?.let { value.viewAsList = it }
                request.queueFollowingSongs?.let { value.queueFollowingSongs = it }
                request.lastFmEnabled?.let { value.lastFmEnabled = it }
                request.listenBrainzEnabled?.let { value.listenBrainzEnabled = it }
                request.listenBrainzUrl?.let { value.listenBrainzUrl = it.ifBlank { null } }
                request.partyModeEnabled?.let { value.partyModeEnabled = it }
                request.nowPlayingAllowed?.let { value.nowPlayingAllowed = it }
                request.paginationSizeFiles?.let { value.paginationSizeFiles = it }
                request.paginationSizeFolders?.let { value.paginationSizeFolders = it }
                request.paginationSizePlaylist?.let { value.paginationSizePlaylist = it }
                request.paginationSizePlayqueue?.let { value.paginationSizePlayqueue = it }
                request.paginationSizeBookmarks?.let { value.paginationSizeBookmarks = it }
                request.autoBookmark?.let { value.autoBookmark = it }
                request.audioBookmarkFrequency?.let { value.audioBookmarkFrequency = it }
                request.videoBookmarkFrequency?.let { value.videoBookmarkFrequency = it }
                request.searchCount?.let { value.searchCount = it }
                request.mainVisibility?.let { value.mainVisibility = it }
                request.playlistVisibility?.let { value.playlistVisibility = it }
                request.playqueueVisibility?.let { value.playqueueVisibility = it }
                value.changed = Instant.now()
                service.updateUserSettings(value)
                value.toUserSettingsResponse()
            }
            call.respondJson(updated)
        } catch (failure: IllegalArgumentException) {
            call.respondJson(ApiError("invalid_request", failure.message ?: "Invalid user settings"), HttpStatusCode.BadRequest)
        }
    }

    get("/me/avatar") {
        val user = call.principal<LocalUserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val service = context.avatarService ?: return@get call.respondJson(
            ApiError(
                "unavailable",
                "Avatar storage is not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val avatar = withContext(authDispatcher) { service.get(user.id) }
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
        call.respondBytes(avatar.bytes, ContentType.Image.PNG)
    }
    put("/me/avatar") {
        val user = call.principal<LocalUserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val service = context.avatarService ?: return@put call.respondJson(
            ApiError(
                "unavailable",
                "Avatar storage is not configured"
            ), HttpStatusCode.ServiceUnavailable)
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
                ApiError(
                    if (tooLarge) "avatar_too_large" else "invalid_avatar",
                    failure.message ?: "Avatar is invalid"
                ),
                if (tooLarge) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest,
            )
        }
    }
    delete("/me/avatar") {
        val user = call.principal<LocalUserPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val service = context.avatarService ?: return@delete call.respondJson(
            ApiError(
                "unavailable",
                "Avatar storage is not configured"
            ), HttpStatusCode.ServiceUnavailable)
        val deleted = withContext(authDispatcher) { service.delete(user.id) }
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
}

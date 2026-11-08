package org.airsonic.player.controller.rest.avatar

import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.domain.Avatar
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.AvatarUpload
import org.subsonic.restapi.PersonalSettingsAvatar
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

/** Website extension. POST multipart field "file"; auth and response parameters belong in the query.
 * Images are limited to 5 MiB/16 megapixels and normalized to PNG at a maximum of 64 x 64.
 * Uploading does not change the selected avatar scheme; personal settings control that separately.
 */
class AvatarUploadController(private val settings: SettingsService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.post(PATH) { respond(call, request(call)) }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest) {
        val response = try {
            if (!request.user.isAdminRole && !request.user.isSettingsRole) {
                writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Personal settings access denied")
            } else {
                var data: ByteArray? = null
                var name: String? = null
                var uploadError: String? = null
                require(call.request.headers["Content-Type"]?.startsWith("multipart/form-data", true) == true) { "Multipart upload required" }
                val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
                require(contentLength == null || contentLength <= MAX_BYTES + 65536L) { "Avatar exceeds 5 MiB" }
                call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
                    try {
                        if (part is PartData.FileItem) {
                            val duplicate = data != null
                            val bytes = withContext(Dispatchers.IO) {
                                part.provider().toInputStream().use { input ->
                                    val bytes = input.readNBytes(MAX_BYTES + 1)
                                    // Finish the part before returning errors, leaving the multipart parser usable.
                                    if (bytes.size > MAX_BYTES) input.transferTo(java.io.OutputStream.nullOutputStream())
                                    bytes
                                }
                            }
                            when {
                                part.name != "file" || duplicate -> uploadError = "Exactly one file field is required"
                                bytes.size > MAX_BYTES -> uploadError = "Avatar exceeds 5 MiB"
                            }
                            if (!duplicate) {
                                name = part.originalFileName?.replace('\\', '/')?.substringAfterLast('/')?.take(255)
                                data = bytes
                            }
                        }
                    } finally { part.dispose() }
                }
                require(uploadError == null) { uploadError!! }
                require(!name.isNullOrBlank() && data?.isNotEmpty() == true) { "Missing file" }
                val uploaded = withContext(Dispatchers.IO) { store(request.user.username, name!!, data!!) }
                writer.createResponse(true).apply { avatarUpload = uploaded }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid avatar")
        } catch (e: Exception) {
            LOG.warn("Avatar upload failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Avatar upload failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun store(username: String, name: String, bytes: ByteArray): AvatarUpload {
        val image = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Unsupported or invalid image" }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) { "Image exceeds 16 megapixels" }
                reader.read(0)
            } finally { reader.dispose() }
        }
        val resized = image.width > MAX_SIDE || image.height > MAX_SIDE
        val scale = minOf(1.0, MAX_SIDE.toDouble() / max(image.width, image.height))
        val width = max(1, (image.width * scale).toInt())
        val height = max(1, (image.height * scale).toInt())
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = output.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(image, 0, 0, width, height, null)
        } finally { graphics.dispose() }
        // Neither account names nor client file names become filesystem paths.
        val account = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(username.toByteArray(Charsets.UTF_8)))
        val folder = SettingsService.getAirsonicHome().resolve("avatars").resolve("uploads").resolve(account)
        Files.createDirectories(folder)
        val previous = settings.getCustomAvatar(username)?.path
        val file = Files.createTempFile(folder, "avatar-", ".png")
        try {
            check(ImageIO.write(output, "png", file.toFile())) { "PNG encoder unavailable" }
            settings.setCustomAvatar(Avatar(0, name, Instant.now(), "image/png", width, height, file), username)
        } catch (e: Exception) {
            Files.deleteIfExists(file)
            throw e
        }
        if (previous != null && previous.parent == folder && previous.fileName.toString().startsWith("avatar-")) {
            try { Files.deleteIfExists(previous) } catch (e: java.io.IOException) { LOG.warn("Unable to remove previous avatar", e) }
        }
        return AvatarUpload().apply {
            this.resized = resized
            avatar = PersonalSettingsAvatar().apply { id = 0; this.name = name; this.width = width; this.height = height }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AvatarUploadController::class.java)
        const val PATH = "/rest/uploadAvatar"
        private const val MAX_BYTES = 5 * 1024 * 1024
        private const val MAX_PIXELS = 16_000_000L
        private const val MAX_SIDE = 64
    }
}

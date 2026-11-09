package org.airsonic.player.controller.rest.upload

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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.StatusService
import org.airsonic.player.service.UploadFileService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.UploadResult
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** Website extension: multipart bodies stream to disk; auth/format parameters belong in the query. */
class UploadController(
    private val files: UploadFileService,
    private val statuses: StatusService,
    private val security: SecurityService,
    private val settings: SettingsService,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        root.post(PATH) {
            val req = request(call)
            call.response.headers.append("Cache-Control", "no-store")
            val uploaded = ArrayList<Path>()
            val extracted = ArrayList<Path>()
            val errors = ArrayList<Exception>()
            val result = try {
                if (!req.user.isAdminRole && !req.user.isUploadRole) throw AccessDeniedException("Upload role required")
                receive(call, req, uploaded, extracted, errors)
                if (errors.isEmpty()) writer.createResponse(true)
                else writer.createErrorResponse(ErrorCode.GENERIC, "Some files could not be uploaded or extracted")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                LOG.warn("Upload failed", e)
                errors.add(e)
                writer.createErrorResponse(if (e is AccessDeniedException) ErrorCode.NOT_AUTHORIZED else ErrorCode.GENERIC,
                    e.message ?: "Upload failed")
            }
            result.uploadResult = UploadResult().apply {
                uploadedFile.addAll(uploaded.map(Path::toString))
                unzippedFile.addAll(extracted.map(Path::toString))
                error.addAll(errors.map { it.message ?: "Upload failed" })
            }
            val serialized = writer.serializeForType(req.parameters["f"] ?: "xml", req.parameters["callback"], result)
            call.respondText(serialized.value, ContentType.parse(serialized.key))
        }
    }

    private suspend fun receive(call: ApplicationCall, req: UsersRequest, uploaded: MutableList<Path>,
                                extracted: MutableList<Path>, errors: MutableList<Exception>) {
        require(call.request.headers["Content-Type"]?.startsWith("multipart/form-data", true) == true) { "Multipart upload required" }
        val status = statuses.createUploadStatus(requireNotNull(req.transferPlayer))
        status.bytesTotal = call.request.headers["Content-Length"]?.toLongOrNull() ?: -1L
        val temporary = ArrayList<Pair<String, Path>>()
        var directory: String? = null
        var extract = false
        try {
            // Media files may exceed Ktor's default part limit. They are never buffered in memory.
            call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FormItem -> when (part.name) {
                            "dir" -> { require(directory == null) { "Duplicate dir" }; directory = part.value }
                            "unzip" -> extract = true
                        }
                        is PartData.FileItem -> {
                            require(part.name == "file") { "Expected file field" }
                            val name = part.originalFileName?.replace('\\', '/')?.substringAfterLast('/')
                            require(!name.isNullOrBlank() && name != "." && name != ".." && name.none(Char::isISOControl)) { "Invalid filename" }
                            withContext(Dispatchers.IO) {
                                val temp = Files.createTempFile("airsonic-upload-", ".tmp")
                                temporary.add(name to temp)
                                status.externalFile = Path.of(name)
                                part.provider().toInputStream().use { input ->
                                    Files.newOutputStream(temp).use { output ->
                                        val buffer = ByteArray(65536)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            settings.uploadBitrateLimiter.acquire(count)
                                            output.write(buffer, 0, count)
                                            status.addBytesTransferred(count.toLong())
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                } finally { part.dispose() }
            }
            require(!directory.isNullOrBlank()) { "Missing dir" }
            require(temporary.isNotEmpty()) { "No files selected" }
            withContext(Dispatchers.IO) {
                val dir = Path.of(directory!!).toAbsolutePath().normalize()
                files.checkAllowed(req.user, dir, false)
                Files.createDirectories(dir)
                for ((name, temp) in temporary) {
                    try { files.store(req.user, temp, name, dir, extract, uploaded, extracted, errors)
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { errors.add(e) }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    for ((_, path) in temporary) {
                        try { Files.deleteIfExists(path)
                        } catch (e: Exception) { LOG.warn("Could not remove temporary upload {}", path, e) }
                    }
                } finally {
                    statuses.removeUploadStatus(status)
                    security.updateUserByteCounts(req.user, 0L, 0L, status.bytesTransferred)
                }
            }
        }
    }

    companion object {
        const val PATH = "/rest/upload"
        private val LOG = LoggerFactory.getLogger(UploadController::class.java)
    }
}

package org.airsonic.player.controller.rest.admin

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.TranscodingService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.TranscodingProfile
import org.subsonic.restapi.TranscodingSettings
import kotlin.coroutines.cancellation.CancellationException

/** Admin extension. Save supplies all existing profiles using name[id], etc., plus optional new profile fields. */
class TranscodingSettingsController(private val service: TranscodingService, private val settings: SettingsService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), false) }
            post(SAVE_PATH) { respond(call, request(call), true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, save: Boolean) {
        val response = try {
            if (!request.user.isAdminRole) writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Administrator access required")
            else {
                if (save) save(request.parameters)
                writer.createResponse(true).apply { if (!save) transcodingSettings = read() }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalArgumentException) {
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid settings")
        } catch (e: Exception) {
            LOG.warn("Transcoding settings administration failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Transcoding settings administration failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun read() = TranscodingSettings().apply {
        brand = settings.brand
        transcodeDirectory = SettingsService.getTranscodeDirectory().toString()
        downsampleCommand = settings.downsamplingCommand.orEmpty()
        hlsCommand = settings.hlsCommand.orEmpty()
        jukeboxCommand = settings.jukeboxCommand.orEmpty()
        videoImageCommand = settings.videoImageCommand.orEmpty()
        subtitlesExtractionCommand = settings.subtitlesExtractionCommand.orEmpty()
        transcodeEstimateTimePadding = settings.transcodeEstimateTimePadding.toString()
        transcodeEstimateBytePadding = settings.transcodeEstimateBytePadding.toString()
        transcoding.addAll(service.allTranscodings.map { source -> TranscodingProfile().apply {
            id = source.id; name = source.name; sourceFormats = source.sourceFormats; targetFormat = source.targetFormat
            step1 = source.step1; step2 = source.step2.orEmpty(); step3 = source.step3.orEmpty(); defaultActive = source.isDefaultActive
        } })
    }

    private fun save(p: Parameters) {
        fun text(key: String) = p[key]?.trim() ?: throw IllegalArgumentException("Missing $key")
        fun optional(key: String) = text(key).takeIf { it.isNotEmpty() }
        fun required(key: String) = optional(key) ?: throw IllegalArgumentException("Required: $key")
        fun bool(key: String) = when (text(key)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: $key")
        }
        fun padding(key: String): Long? {
            val value = optional(key) ?: return null
            return value.toLongOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("Invalid non-negative integer: $key")
        }
        // Build detached records and validate every field before any mutation.
        val current = service.allTranscodings
        val deletes = mutableListOf<Int>()
        val updates = current.mapNotNull { old ->
            val suffix = "[${old.id}]"
            if (bool("delete$suffix")) { deletes.add(old.id); null }
            else Transcoding(old.id, required("name$suffix"), required("sourceFormats$suffix"), required("targetFormat$suffix"),
                required("step1$suffix"), optional("step2$suffix"), old.step3, old.isDefaultActive)
        }
        val newFields = listOf("name", "sourceFormats", "targetFormat", "step1", "step2").map { optional(it) }
        val defaultActive = bool("defaultActive")
        val newProfile = if (newFields.any { it != null }) Transcoding(null, required("name"), required("sourceFormats"),
            required("targetFormat"), required("step1"), optional("step2"), null, defaultActive) else null
        val downsample = text("downsampleCommand"); val hls = text("hlsCommand"); val jukebox = text("jukeboxCommand")
        val image = text("videoImageCommand"); val subtitles = text("subtitlesExtractionCommand")
        val time = padding("transcodeEstimateTimePadding"); val bytes = padding("transcodeEstimateBytePadding")
        updates.forEach(service::updateTranscoding)
        deletes.forEach(service::deleteTranscoding)
        newProfile?.let(service::createTranscoding)
        settings.downsamplingCommand = downsample; settings.hlsCommand = hls; settings.jukeboxCommand = jukebox
        settings.videoImageCommand = image; settings.subtitlesExtractionCommand = subtitles
        settings.setTranscodeEstimateTimePadding(time); settings.setTranscodeEstimateBytePadding(bytes)
        settings.save()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TranscodingSettingsController::class.java)
        const val READ_PATH = "/rest/getTranscodingSettings"
        const val SAVE_PATH = "/rest/saveTranscodingSettings"
    }
}

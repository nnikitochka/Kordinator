package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import java.io.InputStream
import java.util.Locale

/** Selects configured audio profiles without creating legacy users or players. */
class StandaloneAudioTranscodingService(private val transcoding: TranscodingService) {
    data class Plan(val contentType: String, val parameters: TranscodingService.Parameters)

    fun prepare(file: MediaFile, format: String, bitrateKbps: Int): Plan? {
        require(!file.isVideo && !file.isDirectory) { "Only audio files can be transcoded" }
        require(bitrateKbps in 32..320) { "Bitrate must be between 32 and 320 kbps" }
        val target = format.lowercase(Locale.ROOT)
        val contentType = CONTENT_TYPES[target] ?: return null
        val profile = transcoding.allTranscodings.firstOrNull { profile ->
            profile.targetFormat.equals(target, ignoreCase = true) &&
                profile.sourceFormatsAsArray.any { it.equals(file.format, ignoreCase = true) }
        }
        val downsample = profile == null && target == "mp3" && file.format.equals("mp3", ignoreCase = true)
        if (profile == null && !downsample) return null
        val parameters = TranscodingService.Parameters(file, null, bitrateKbps).apply {
            setTranscoding(profile)
            setDownsample(downsample)
        }
        return Plan(contentType, parameters)
    }

    fun open(plan: Plan): InputStream = transcoding.openTranscodedInputStream(plan.parameters)

    private companion object {
        val CONTENT_TYPES = mapOf(
            "mp3" to "audio/mpeg", "opus" to "audio/ogg", "ogg" to "audio/ogg",
            "aac" to "audio/aac", "flac" to "audio/flac", "wav" to "audio/wav",
        )
    }
}

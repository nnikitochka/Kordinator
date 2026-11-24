package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.util.StringUtil
import java.io.InputStream
import java.util.Locale

/** Selects configured audio profiles without creating legacy users or players. */
class StandaloneAudioTranscodingService(private val transcoding: TranscodingService) {
    data class Plan(val contentType: String, val parameters: TranscodingService.Parameters)

    fun prepare(file: MediaFile, format: String, bitrateKbps: Int, startSeconds: Double = 0.0): Plan? {
        require(!file.isVideo && !file.isDirectory) { "Only audio files can be transcoded" }
        require(bitrateKbps in 32..320) { "Bitrate must be between 32 and 320 kbps" }
        require(startSeconds.isFinite() && startSeconds >= 0) { "startSeconds must be finite and non-negative" }
        val duration = file.duration
        require(startSeconds == 0.0 || (duration != null && duration.isFinite() && startSeconds < duration)) {
            "startSeconds must be smaller than the known track duration"
        }
        val target = format.lowercase(Locale.ROOT)
        val contentType = CONTENT_TYPES[target] ?: return null
        val profile = transcoding.allTranscodings.firstOrNull { profile ->
            profile.targetFormat.equals(target, ignoreCase = true) &&
                profile.getSourceFormatsAsArray().any { it.equals(file.format, ignoreCase = true) }
        }
        val downsample = profile == null && target == "mp3" && file.format.equals("mp3", ignoreCase = true)
        if (profile == null && !downsample) return null
        val command = if (downsample) transcoding.audioDownsamplingCommand else requireNotNull(profile!!.step1)
        if (startSeconds > 0 && !supportsSeeking(command)) return null
        val parameters = TranscodingService.Parameters(file, null, bitrateKbps).apply {
            setTranscoding(profile)
            setDownsample(downsample)
            setAudioStartSeconds(startSeconds)
        }
        return Plan(contentType, parameters)
    }

    fun open(plan: Plan): InputStream = transcoding.openTranscodedInputStream(plan.parameters)

    private fun supportsSeeking(command: String): Boolean {
        if (command.contains("%o")) return true
        val executable = StringUtil.split(command).firstOrNull()?.substringAfterLast('/')?.substringAfterLast('\\')
        return executable.equals("ffmpeg", true) || executable.equals("ffmpeg.exe", true)
    }

    private companion object {
        val CONTENT_TYPES = mapOf(
            "mp3" to "audio/mpeg", "opus" to "audio/ogg", "ogg" to "audio/ogg",
            "aac" to "audio/aac", "flac" to "audio/flac", "wav" to "audio/wav",
        )
    }
}

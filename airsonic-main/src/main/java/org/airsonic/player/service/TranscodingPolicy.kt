package org.airsonic.player.service

import org.airsonic.player.dao.TranscodingDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.TranscodeScheme
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.domain.VideoTranscodingSettings
import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory
import org.apache.commons.lang3.StringUtils

/** Applies player settings and enabled profiles to transcoding decisions and response metadata. */
internal class TranscodingPolicy(
    private val transcodingDao: TranscodingDao,
    private val settingsService: SettingsService,
) {
    fun isRequired(mediaFile: MediaFile, player: Player?) =
        findTranscoding(mediaFile, player, null, false) != null

    fun suffix(player: Player?, file: MediaFile, preferredTargetFormat: String?): String? =
        findTranscoding(file, player, preferredTargetFormat, false)?.targetFormat ?: file.format

    fun parameters(
        mediaFile: MediaFile,
        player: Player?,
        requestedMaxBitRate: Int?,
        preferredTargetFormat: String?,
        videoSettings: VideoTranscodingSettings?,
    ): TranscodingService.Parameters {
        val result = TranscodingService.Parameters(mediaFile, videoSettings)
        var maxBitRate = requestedMaxBitRate
        val scheme = transcodeScheme(player)
        if (maxBitRate == null && scheme != TranscodeScheme.OFF) maxBitRate = scheme.maxBitRate

        val hls = videoSettings?.hlsSegmentFilename != null
        val transcoding = findTranscoding(mediaFile, player, preferredTargetFormat, hls)
        if (transcoding != null) {
            result.transcoding = transcoding
            if (maxBitRate == null) {
                maxBitRate = if (mediaFile.isVideo) TranscodingService.DEFAULT_VIDEO_BIT_RATE else TranscodeScheme.MAX_192.maxBitRate
            }
        } else if (maxBitRate != null) {
            val bitRate = mediaFile.bitRate
            if (isDownsamplingSupported(mediaFile) && bitRate != null && bitRate > maxBitRate) {
                result.isDownsample = true
            }
        }
        result.maxBitRate = maxBitRate
        result.expectedLength = expectedLength(result)
        result.isRangeAllowed = rangeAllowed(result)
        return result
    }

    fun isDownsamplingSupported(mediaFile: MediaFile?): Boolean {
        if (mediaFile != null && !"mp3".equals(mediaFile.format, ignoreCase = true)) return false
        return isTranscodingStepInstalled(settingsService.getDownsamplingCommand())
    }

    fun expectedLength(parameters: TranscodingService.Parameters): Long? {
        val file = parameters.mediaFile
        if (!parameters.isDownsample && !parameters.isTranscode) return file.fileSize
        val duration = file.duration
        val maxBitRate = parameters.maxBitRate
        if (duration == null) {
            log.warn("Unknown duration for {}. Unable to estimate transcoded size.", file)
            return null
        }
        if (maxBitRate == null) {
            log.error("Unknown bit rate for {}. Unable to estimate transcoded size.", file)
            return null
        }
        return Math.round((duration + settingsService.transcodeEstimateTimePadding / 1000.0) * maxBitRate * 1000L / 8L) +
            settingsService.transcodeEstimateBytePadding
    }

    fun rangeAllowed(parameters: TranscodingService.Parameters): Boolean {
        val transcoding = parameters.transcoding
        val steps = when {
            transcoding != null -> listOf(transcoding.step3, transcoding.step2, transcoding.step1)
            parameters.isDownsample -> listOf(settingsService.getDownsamplingCommand())
            else -> return true
        }
        if (parameters.expectedLength == null) return false
        return steps.firstOrNull { it != null }?.contains("%b") ?: false
    }

    private fun transcodeScheme(player: Player?): TranscodeScheme {
        if (player == null) return TranscodeScheme.OFF
        val username = player.username ?: return player.transcodeScheme
        return player.transcodeScheme.strictest(settingsService.getUserSettings(username).transcodeScheme)
    }

    private fun findTranscoding(
        mediaFile: MediaFile,
        player: Player?,
        preferredTargetFormat: String?,
        hls: Boolean,
    ): Transcoding? {
        if (hls) return Transcoding(null, "hls", mediaFile.format, "ts", settingsService.getHlsCommand(), null, null, true)
        if (TranscodingService.FORMAT_RAW == preferredTargetFormat) return null

        val suffix = mediaFile.format
        val applicable = ArrayList<Transcoding>()
        for (transcoding in transcodingDao.getTranscodingsForPlayer(player?.id ?: return null)) {
            if (mediaFile.isVideo && StringUtils.equalsIgnoreCase(preferredTargetFormat, transcoding.targetFormat)) {
                log.debug("Detected source to target format match for video")
                return transcoding
            }
            if (transcoding.getSourceFormatsAsArray().any { it.equals(suffix, ignoreCase = true) } && isTranscodingInstalled(transcoding)) {
                applicable.add(transcoding)
            }
        }
        if (applicable.isEmpty()) return null
        return applicable.firstOrNull { it.targetFormat.equals(preferredTargetFormat, ignoreCase = true) } ?: applicable.first()
    }

    private fun isTranscodingInstalled(transcoding: Transcoding) =
        isTranscodingStepInstalled(transcoding.step1) &&
            isTranscodingStepInstalled(transcoding.step2) &&
            isTranscodingStepInstalled(transcoding.step3)

    private fun isTranscodingStepInstalled(step: String?): Boolean {
        if (StringUtils.isEmpty(step)) return true
        val executable = StringUtil.split(step)[0]
        return SettingsService.resolveTranscodeExecutable(executable, null) != null
    }

    companion object {
        private val log = LoggerFactory.getLogger(TranscodingPolicy::class.java)
    }
}

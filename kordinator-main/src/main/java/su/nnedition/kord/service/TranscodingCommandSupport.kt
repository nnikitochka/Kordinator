package su.nnedition.kord.service

import su.nnedition.kord.util.StringUtil
import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.math.roundToInt

/** Builds external transcoder commands and computes bitrate-derived media dimensions. */
internal object TranscodingCommandSupport {
    fun transform(command: String, variables: Map<String, String?>): ProcessBuilder {
        val splitCommand = StringUtil.split(command)
        val keys = ArrayList<String>(variables.size)
        val values = ArrayList<String>(variables.size)
        variables.entries.stream().filter { it.value != null }.forEach {
            keys.add(it.key)
            values.add(it.value!!)
        }
        val keyArray = keys.toTypedArray()
        val valueArray = values.toTypedArray()
        val args = IntStream.range(0, splitCommand.size)
            .mapToObj { index -> substitute(index, splitCommand[index], keyArray, valueArray) }
            .collect(Collectors.toList())
        return ProcessBuilder(args)
    }

    fun substitute(index: Int, segment: String, keys: Array<String>, values: Array<String>): String =
        if (index == 0) requireNotNull(SettingsService.resolveTranscodeExecutable(segment, segment))
        else StringUtils.replaceEach(segment, keys, values)

    fun substitutionMap(
        title: String,
        artist: String,
        album: String,
        maxVideoBitRate: String?,
        timeOffset: String?,
        duration: String?,
        width: String?,
        height: String?,
        averageVideoRate: String?,
        audioRate: String?,
        audioTrackIndex: String?,
        hlsSegmentIndex: String?,
        hlsSegmentFilename: String?,
        inputFilename: String,
        outputFilename: String?,
    ): Map<String, String?> = hashMapOf(
        "%t" to title,
        "%a" to artist,
        "%l" to album,
        "%b" to maxVideoBitRate,
        "%o" to timeOffset,
        "%d" to duration,
        "%w" to width,
        "%h" to height,
        "%v" to averageVideoRate,
        "%r" to audioRate,
        "%i" to audioTrackIndex,
        "%j" to hlsSegmentIndex,
        "%n" to hlsSegmentFilename,
        "%s" to inputFilename,
        "%p" to outputFilename,
    )

    fun suitableVideoSize(existingWidth: Int?, existingHeight: Int?, peakVideoBitRate: Int): Dimension {
        val width = when {
            peakVideoBitRate < 400 -> 416
            peakVideoBitRate < 800 -> 480
            peakVideoBitRate < 1200 -> 640
            peakVideoBitRate < 2200 -> 768
            peakVideoBitRate < 3300 -> 960
            peakVideoBitRate < 8600 -> 1280
            else -> 1920
        }
        var height = even(width * 9 / 16)
        if (existingWidth == null || existingHeight == null) return Dimension(width, height)
        if (existingWidth < width || existingHeight < height) {
            return Dimension(even(existingWidth), even(existingHeight))
        }
        val aspectRatio = existingWidth.toDouble() / existingHeight
        height = (width / aspectRatio).roundToInt()
        return Dimension(even(width), even(height))
    }

    private fun even(size: Int) = size + size % 2

    fun suitableAudioBitRate(peakVideoBitRate: Int): Int = when {
        peakVideoBitRate < 1200 -> 64
        peakVideoBitRate < 5000 -> 96
        else -> 128
    }

    fun averageVideoBitRate(peakVideoBitRate: Int): Int = when (peakVideoBitRate) {
        200 -> 145
        400 -> 365
        800 -> 730
        1200 -> 1100
        2200 -> 2000
        3300 -> 3000
        5000 -> 4500
        6500 -> 6000
        else -> (peakVideoBitRate * 0.9).toInt()
    }
}

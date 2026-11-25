package org.airsonic.player.service

import com.google.common.io.MoreFiles
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.io.TranscodeInputStream
import org.airsonic.player.util.Util
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Creates transcoder process streams and owns temporary input path handling. */
internal class TranscodingStreamFactory(
    private val settingsService: SettingsService,
    private val mediaFolderService: MediaFolderService,
) {
    @Throws(IOException::class)
    fun create(transcoding: Transcoding, parameters: TranscodingService.Parameters): InputStream {
        var input = createStep(transcoding.step1!!, parameters, null, parameters.audioStartSeconds)
        try {
            transcoding.step2?.let { input = createStep(it, parameters, input) }
            transcoding.step3?.let { input = createStep(it, parameters, input) }
        } catch (failure: IOException) {
            input.close()
            throw failure
        } catch (failure: RuntimeException) {
            input.close()
            throw failure
        }
        return input
    }

    @Throws(IOException::class)
    fun createDownsampled(parameters: TranscodingService.Parameters): InputStream = createStep(
        settingsService.getDownsamplingCommand(), parameters, null, parameters.audioStartSeconds,
    )

    @Throws(IOException::class)
    fun openOriginal(mediaFile: MediaFile): InputStream = Files.newInputStream(mediaPath(mediaFile).toAbsolutePath()).buffered()

    @Throws(IOException::class)
    private fun createStep(
        command: String,
        parameters: TranscodingService.Parameters,
        input: InputStream?,
        audioStartSeconds: Double? = null,
    ): TranscodeInputStream {
        val mediaFile = parameters.mediaFile
        val path = mediaPath(mediaFile).toAbsolutePath()
        var pathString = path.toString()
        var tempFile: Path? = null
        if (Util.isWindows() && !mediaFile.isVideo && !StringUtils.isAsciiPrintable(pathString) && StringUtils.contains(command, "%s")) {
            tempFile = Files.createTempFile("airsonic", ".${MoreFiles.getFileExtension(path)}")
            tempFile.toFile().deleteOnExit()
            Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING)
            log.info("Created tmp file: {}", tempFile)
            pathString = tempFile.toString()
        }

        val maxBitRate = parameters.maxBitRate
        val videoSettings = parameters.videoTranscodingSettings
        val variables = TranscodingCommandSupport.substitutionMap(
            mediaFile.title ?: "Unknown Media",
            mediaFile.artist ?: "Unknown Artist",
            mediaFile.albumName ?: "Unknown Album",
            maxBitRate?.toString(),
            audioStartSeconds?.toString() ?: videoSettings?.timeOffset?.toString(),
            videoSettings?.duration?.toString(),
            videoSettings?.width?.toString(),
            videoSettings?.height?.toString(),
            maxBitRate?.let(TranscodingCommandSupport::averageVideoBitRate)?.toString(),
            maxBitRate?.let(TranscodingCommandSupport::suitableAudioBitRate)?.toString(),
            videoSettings?.audioTrackIndex?.toString(),
            videoSettings?.hlsSegmentIndex?.toString(),
            videoSettings?.hlsSegmentFilename,
            pathString,
            videoSettings?.outputFilename,
        )
        val builder = TranscodingCommandSupport.transform(command, variables)
        if (audioStartSeconds != null) {
            val executable = Path.of(builder.command().first()).fileName.toString()
            val ffmpeg = executable.equals("ffmpeg", ignoreCase = true) || executable.equals("ffmpeg.exe", ignoreCase = true)
            if (ffmpeg && "-nostdin" !in builder.command()) builder.command().add(1, "-nostdin")
            if (audioStartSeconds > 0 && !command.contains("%o")) {
                require(ffmpeg) { "Audio profile does not support time seeking" }
                builder.command().addAll(1, listOf("-ss", audioStartSeconds.toString()))
            }
        }
        return TranscodeInputStream(builder, input, tempFile)
    }

    private fun mediaPath(mediaFile: MediaFile) =
        mediaFile.getFullPath(mediaFolderService.getMusicFolderById(mediaFile.folderId)!!.path)

    companion object {
        private val log = LoggerFactory.getLogger(TranscodingStreamFactory::class.java)
    }
}

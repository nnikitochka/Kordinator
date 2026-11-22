package org.airsonic.player.service

import com.google.common.io.MoreFiles
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.lang3.StringUtils
import org.airsonic.player.controller.captions.CaptionInfo
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.io.InputStreamReaderThread
import org.airsonic.player.service.metadata.MetaData
import org.airsonic.player.service.metadata.MetaDataParser
import org.airsonic.player.service.metadata.MetaDataParserFactory
import org.slf4j.LoggerFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

open class CaptionService(
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private val settingsService: SettingsService,
    private val metaDataParserFactory: MetaDataParserFactory,
    private val jwtSecurityService: JWTSecurityService
) {
    @Throws(IOException::class)
    open fun openConverted(inputFile: Path, identifier: String, format: String): InputStream {
        val split = StringUtils.split(settingsService.subtitlesExtractionCommand)!!
        val command = split.map { argument ->
            when {
                argument == split[0] -> SettingsService.resolveTranscodeExecutable(argument, argument)
                argument.contains("%s") -> argument.replace("%s", inputFile.toString())
                argument.contains("%i") -> argument.replace("%i", identifier)
                argument.contains("%f") -> argument.replace("%f", getForceFormat(format))
                else -> argument
            }
        }
        val process = ProcessBuilder(command).start()

        // Must read stderr from the process, otherwise it may block.
        InputStreamReaderThread(
            process.errorStream,
            "subs-extraction-error-stream-$inputFile",
            true
        ).start()
        return object : FilterInputStream(process.inputStream) {
            @Throws(IOException::class)
            override fun close() {
                try {
                    super.close()
                } finally {
                    process.destroy()
                }
            }
        }
    }

    open fun listCaptions(video: MediaFile, basePath: String): List<CaptionInfo> =
        listCaptions(video, basePath, null, null)

    open fun listCaptions(
        video: MediaFile,
        basePath: String,
        externalUser: String?,
        externalExpiration: Instant?
    ): List<CaptionInfo> {
        val metaData = getVideoMetaData(video)
        val internalCaptions = metaData?.subtitleTracks.orEmpty().map { track ->
            CaptionInfo(
                track.id.toString(),
                CaptionInfo.Location.INTERNAL,
                getDisplayFormat(track.codec!!),
                track.language!!,
                getUrl(basePath, externalUser, externalExpiration, video.id, track.id.toString())
            )
        }
        val externalCaptions = findExternalCaptionsForVideo(video).map { caption ->
            CaptionInfo(
                caption.toString(), // leaks internal structure for now
                CaptionInfo.Location.EXTERNAL,
                MoreFiles.getFileExtension(caption),
                caption.fileName.toString(),
                getUrl(
                    basePath,
                    externalUser,
                    externalExpiration,
                    video.id,
                    URLEncoder.encode(caption.toString(), StandardCharsets.UTF_8)
                )
            )
        }
        return internalCaptions + externalCaptions
    }

    open fun getVideoMetaData(video: MediaFile): MetaData? {
        val videoFullPath = video.getFullPath(mediaFolderService.getMusicFolderById(video.folderId).path)
        val parser: MetaDataParser? = metaDataParserFactory.getParser(videoFullPath)
        return parser?.getMetaData(videoFullPath)
    }

    open fun getUrl(
        basePath: String,
        externalUser: String?,
        externalExpiration: Instant?,
        mediaId: Int,
        captionId: String
    ): String {
        val external = !StringUtils.isBlank(externalUser)
        var url = (if (external) "ext/" else "") + "rest/captions?id=$mediaId&captionId=$captionId"
        if (external) url = jwtSecurityService.addJWTToken(externalUser!!, url, externalExpiration)
        return basePath + url
    }

    @Throws(IOException::class)
    open fun openExternal(captionsFile: Path, format: String): InputStream {
        val input = Files.newInputStream(captionsFile)
        return if (format == "vtt") input else BOMInputStream(input)
    }

    open fun findExternalCaptionsForVideo(video: MediaFile): List<Path> {
        val parent = mediaFileService.getParentOf(video) ?: return emptyList()
        val parentPath = parent.getFullPath(mediaFolderService.getMusicFolderById(parent.folderId).path)
        return try {
            Files.walk(parentPath).use { children ->
                children.filter(Files::isRegularFile)
                    .filter { CAPTION_FORMATS.contains(MoreFiles.getFileExtension(it)) }
                    .toList()
            }
        } catch (e: IOException) {
            LOG.warn("Could not retrieve directory list for {} to find subtitle files for {}", parentPath, video, e)
            emptyList()
        }
    }

    companion object {
        private const val CAPTION_FORMAT_VTT = "vtt"
        private const val CAPTION_FORMAT_SRT = "srt"
        private val CAPTION_FORMATS = setOf(CAPTION_FORMAT_VTT, CAPTION_FORMAT_SRT)
        private val LOG = LoggerFactory.getLogger(CaptionService::class.java)

        @JvmStatic
        fun getForceFormat(format: String): String = if (format == "vtt") "webvtt" else format

        @JvmStatic
        fun getDisplayFormat(format: String): String = when (format) {
            "webvtt" -> "vtt"
            "subrip" -> "srt"
            else -> format
        }
    }
}

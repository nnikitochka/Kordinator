package su.nnedition.kord.service.metadata

import com.fasterxml.jackson.databind.JsonNode
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.util.Util
import org.apache.commons.lang3.math.NumberUtils
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Reads media streams and tags using ffprobe. */
class FFmpegParser(private val mediaFolders: MediaFolderService) : MetaDataParser() {
    override fun getRawMetaData(file: Path): MetaData {
        val metadata = MetaData()
        try {
            val ffprobe = SettingsService.resolveTranscodeExecutable("ffprobe", "ffprobe")
            val command = listOf(ffprobe, "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", file.toAbsolutePath().toString())
            val process = Runtime.getRuntime().exec(command.toTypedArray())
            val result = process.inputStream.use { input ->
                BufferedInputStream(input).use { Util.getObjectMapper().readTree(it) }
            }
            populateMetadata(result, metadata)
        } catch (failure: Throwable) {
            LOG.warn("Error when parsing metadata in {}", file, failure)
        }
        return metadata
    }

    override fun setMetaData(file: MediaFile, metaData: MetaData) {
        throw RuntimeException("setMetaData() not supported in ${javaClass.simpleName}")
    }

    override fun isEditingSupported(): Boolean = false
    override fun isApplicable(path: Path): Boolean = Files.isRegularFile(path)
    override fun getOrder(): Int = 100
    override fun getMediaFolderService(): MediaFolderService = mediaFolders

    companion object {
        private val LOG = LoggerFactory.getLogger(FFmpegParser::class.java)
    }
}

internal fun populateMetadata(result: JsonNode, metadata: MetaData) {
    metadata.duration = result.at("/format/duration").asDouble()
    metadata.bitRate = result.at("/format/bit_rate").asInt() / 1000
    metadata.albumArtist = ffprobeTag(result, "album_artist")
    metadata.artist = ffprobeTag(result, "artist")
    metadata.albumName = ffprobeTag(result, "album")
    metadata.genre = ffprobeTag(result, "genre")
    metadata.title = ffprobeTag(result, "title")
    val track = ffprobeTag(result, "track")
    if (NumberUtils.isCreatable(track)) metadata.trackNumber = NumberUtils.createInteger(track)
    val year = ffprobeTag(result, "date")
    if (NumberUtils.isCreatable(year)) metadata.year = NumberUtils.createInteger(year)
    for (stream in result.at("/streams")) {
        val mediaTrack = Track(stream.get("index").asInt(), stream.get("codec_type").asText(),
            stream.at("/tags/language").asText(), stream.get("codec_name").asText())
        metadata.addTrack(mediaTrack)
        if (mediaTrack.isVideo && stream.has("width") && stream.has("height")) {
            metadata.width = stream.get("width").asInt()
            metadata.height = stream.get("height").asInt()
        }
    }
}

private fun ffprobeTag(node: JsonNode, key: String): String? {
    val keys = listOf("/tags/$key", "/tags/${key.uppercase(Locale.getDefault())}", "/tags/${key.lowercase(Locale.getDefault())}")
    keys.firstNotNullOfOrNull { node.at("/format$it").asText().takeIf(String::isNotBlank) }?.let { return it }
    for (stream in node.at("/streams")) {
        if (stream.hasNonNull("codec_name") && stream.get("codec_name").asText().equals("mjpeg", ignoreCase = true)) continue
        keys.firstNotNullOfOrNull { stream.at(it).asText().takeIf(String::isNotBlank) }?.let { return it }
    }
    return null
}

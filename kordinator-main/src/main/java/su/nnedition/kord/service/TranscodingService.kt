package su.nnedition.kord.service

import su.nnedition.kord.dao.TranscodingDao
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.Player
import su.nnedition.kord.domain.Transcoding
import su.nnedition.kord.domain.VideoTranscodingSettings
import su.nnedition.kord.service.media.MediaFolderService
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.io.IOException
import java.io.InputStream
import java.util.function.Supplier

/** Coordinates transcoding profiles, policy decisions and process-backed input streams. */
class TranscodingService(
    private var transcodingDao: TranscodingDao,
    private var settingsService: SettingsService,
    private val mediaFolderService: MediaFolderService,
    private var playerServiceSupplier: Supplier<PlayerService>,
) {
    private var policy = TranscodingPolicy(transcodingDao, settingsService)
    private var streams = TranscodingStreamFactory(settingsService, mediaFolderService)

    val allTranscodings: List<Transcoding> get() = transcodingDao.allTranscodings

    fun getTranscodingsForPlayer(player: Player): List<Transcoding> = transcodingDao.getTranscodingsForPlayer(player.id)

    fun setTranscodingsForPlayer(player: Player, transcodingIds: IntArray) {
        transcodingDao.setTranscodingsForPlayer(player.id, transcodingIds)
    }

    fun setTranscodingsForPlayer(player: Player, transcodings: List<Transcoding>) {
        setTranscodingsForPlayer(player, transcodings.map { it.id!! }.toIntArray())
    }

    fun createTranscoding(transcoding: Transcoding) {
        transcodingDao.createTranscoding(transcoding)
        if (transcoding.defaultActive) {
            playerServiceSupplier.get().getAllPlayers().filterNotNull().parallelStream().forEach { player ->
                val enabled = ArrayList(getTranscodingsForPlayer(player))
                enabled.add(transcoding)
                setTranscodingsForPlayer(player, enabled)
            }
        }
    }

    fun deleteTranscoding(id: Int?) {
        transcodingDao.deleteTranscoding(id)
    }

    fun updateTranscoding(transcoding: Transcoding) {
        transcodingDao.updateTranscoding(transcoding)
    }

    fun isTranscodingRequired(mediaFile: MediaFile, player: Player): Boolean = policy.isRequired(mediaFile, player)

    fun getSuffix(player: Player?, file: MediaFile, preferredTargetFormat: String?): String? =
        policy.suffix(player, file, preferredTargetFormat)

    fun getParameters(
        mediaFile: MediaFile,
        player: Player?,
        maxBitRate: Int?,
        preferredTargetFormat: String?,
        videoTranscodingSettings: VideoTranscodingSettings?,
    ): Parameters = policy.parameters(mediaFile, player, maxBitRate, preferredTargetFormat, videoTranscodingSettings)

    @Throws(IOException::class)
    fun getTranscodedInputStream(parameters: Parameters): InputStream {
        try {
            parameters.transcoding?.let { return streams.create(it, parameters) }
            if (parameters.isDownsample) return streams.createDownsampled(parameters)
        } catch (failure: IOException) {
            log.warn(
                "Transcoder failed for {} in folder {}. Using original file",
                parameters.mediaFile.path,
                parameters.mediaFile.folderId,
                failure,
            )
        } catch (failure: Exception) {
            log.warn(
                "Transcoder failed for {} in folder {}. Using original file",
                parameters.mediaFile.path,
                parameters.mediaFile.folderId,
                failure,
            )
        }
        return streams.openOriginal(parameters.mediaFile)
    }

    /** Opens an explicit profile without silently returning the original file on failure. */
    @Throws(IOException::class)
    fun openTranscodedInputStream(parameters: Parameters): InputStream = when {
        parameters.transcoding != null -> streams.create(parameters.transcoding!!, parameters)
        parameters.isDownsample -> streams.createDownsampled(parameters)
        else -> throw IllegalArgumentException("An explicit transcoding profile or downsampling request is required")
    }

    val audioDownsamplingCommand: String get() = settingsService.getDownsamplingCommand()

    fun isDownsamplingSupported(mediaFile: MediaFile?): Boolean = policy.isDownsamplingSupported(mediaFile)

    fun setTranscodingDao(transcodingDao: TranscodingDao) {
        this.transcodingDao = transcodingDao
        policy = TranscodingPolicy(this.transcodingDao, settingsService)
    }

    fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
        policy = TranscodingPolicy(transcodingDao, this.settingsService)
        streams = TranscodingStreamFactory(this.settingsService, mediaFolderService)
    }

    companion object {
        const val FORMAT_RAW = "raw"
        const val DEFAULT_VIDEO_BIT_RATE = 1500
        private val log = LoggerFactory.getLogger(TranscodingService::class.java)

        @JvmStatic
        fun transformTranscodingVariables(command: String, vars: Map<String, String?>): ProcessBuilder =
            TranscodingCommandSupport.transform(command, vars)

        @JvmStatic
        fun substituteTranscodingVariable(index: Int, commandSegment: String, keys: Array<String>, values: Array<String>): String =
            TranscodingCommandSupport.substitute(index, commandSegment, keys, values)

        @JvmStatic
        fun generateTranscodingSubstitutionMap(
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
        ): Map<String, String?> = TranscodingCommandSupport.substitutionMap(
            title, artist, album, maxVideoBitRate, timeOffset, duration, width, height,
            averageVideoRate, audioRate, audioTrackIndex, hlsSegmentIndex, hlsSegmentFilename,
            inputFilename, outputFilename,
        )

        @JvmStatic
        fun getSuitableVideoSize(existingWidth: Int?, existingHeight: Int?, peakVideoBitRate: Int): Dimension =
            TranscodingCommandSupport.suitableVideoSize(existingWidth, existingHeight, peakVideoBitRate)

        @JvmStatic
        fun getSuitableAudioBitRate(peakVideoBitRate: Int): Int = TranscodingCommandSupport.suitableAudioBitRate(peakVideoBitRate)

        @JvmStatic
        fun getAverageVideoBitRate(peakVideoBitRate: Int): Int = TranscodingCommandSupport.averageVideoBitRate(peakVideoBitRate)
    }

    class Parameters {
        var isDownsample = false
        var expectedLength: Long? = null
        var isRangeAllowed = false
        val mediaFile: MediaFile
        val videoTranscodingSettings: VideoTranscodingSettings?
        var maxBitRate: Int? = null
        var transcoding: Transcoding? = null
        val isTranscode: Boolean get() = transcoding != null
        var audioStartSeconds: Double? = null
            set(value) {
                require(value == null || value.isFinite() && value >= 0) {
                    "Audio start must be finite and non-negative"
                }
                field = value
            }

        constructor(mediaFile: MediaFile, videoTranscodingSettings: VideoTranscodingSettings?) {
            this.mediaFile = mediaFile
            this.videoTranscodingSettings = videoTranscodingSettings
        }

        constructor(mediaFile: MediaFile, videoTranscodingSettings: VideoTranscodingSettings?, maxBitrate: Int?) :
            this(mediaFile, videoTranscodingSettings) {
            this.maxBitRate = maxBitrate
        }

    }
}

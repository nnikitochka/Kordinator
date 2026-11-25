package su.nnedition.kord.domain

/** Parameters used when transcoding videos. */
class VideoTranscodingSettings @JvmOverloads constructor(
    val width: Int,
    val height: Int,
    val timeOffset: Int,
    val duration: Double,
    val audioTrackIndex: Int = 1,
    val hlsSegmentIndex: Int = 0,
    val hlsSegmentFilename: String? = null,
    val outputFilename: String? = null
)

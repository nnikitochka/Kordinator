package su.nnedition.kord.api.module.admin.settings

data class TranscodingSettingsResponse(
    val brand: String, val transcodeDirectory: String, val downsampleCommand: String,
    val hlsCommand: String, val jukeboxCommand: String, val videoImageCommand: String,
    val subtitlesExtractionCommand: String, val estimateTimePadding: Long, val estimateBytePadding: Long,
    val profiles: List<TranscodingProfileResponse>,
)

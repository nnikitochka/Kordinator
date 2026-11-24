package su.nnedition.kord.api.module.admin.settings

data class UpdateTranscodingSettingsRequest(
    val profiles: List<TranscodingProfileRequest> = emptyList(),
    val deleteIds: Set<Int> = emptySet(),
    val downsampleCommand: String,
    val hlsCommand: String,
    val jukeboxCommand: String,
    val videoImageCommand: String,
    val subtitlesExtractionCommand: String,
    val estimateTimePadding: Long? = null,
    val estimateBytePadding: Long? = null,
)

package su.nnedition.kord.api.module.lyric

data class LyricsResponse(
    val artist: String?,
    val title: String?,
    val lyrics: String?,
    val tryLater: Boolean?,
)
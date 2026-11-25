package su.nnedition.kord.api.module.jukebox

data class JukeboxCommandRequest(
    val action: String,
    val index: Int? = null,
    val offsetSeconds: Int? = null,
    val gain: Float? = null,
)

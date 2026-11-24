package su.nnedition.kord.api.module.playlist

data class SavePlaylistRequest(
    val name: String,
    val trackIds: List<Long> = emptyList(),
    val visibility: String = "PRIVATE",
    val description: String? = null,
)
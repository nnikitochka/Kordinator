package su.nnedition.kord.api.module.library

data class KordinatorLibraryEntry(
    val id: Int,
    val type: String,
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Int? = null,
    val format: String? = null,
)

package su.nnedition.kord.api.module.admin.settings

data class UpdateMusicFolderEntry(
    val id: Int? = null,
    val name: String,
    val path: String,
    val type: String = "MEDIA",
    val enabled: Boolean = true,
    val delete: Boolean = false,
)

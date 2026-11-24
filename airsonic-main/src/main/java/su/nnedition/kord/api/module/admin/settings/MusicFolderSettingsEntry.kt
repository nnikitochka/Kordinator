package su.nnedition.kord.api.module.admin.settings

data class MusicFolderSettingsEntry(
    val id: Int,
    val name: String,
    val path: String,
    val type: String,
    val enabled: Boolean,
    val exists: Boolean,
    val overlap: Boolean,
    val overlapDescription: String,
)

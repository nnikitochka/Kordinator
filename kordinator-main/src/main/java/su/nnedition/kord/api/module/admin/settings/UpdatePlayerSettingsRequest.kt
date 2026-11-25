package su.nnedition.kord.api.module.admin.settings

data class UpdatePlayerSettingsRequest(
    val name: String = "",
    val dynamicIp: Boolean,
    val autoControlEnabled: Boolean,
    val m3uBomEnabled: Boolean,
    val technology: String,
    val transcodeScheme: String,
    val transcodingIds: List<Int> = emptyList(),
)

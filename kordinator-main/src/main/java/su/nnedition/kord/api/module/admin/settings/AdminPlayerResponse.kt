package su.nnedition.kord.api.module.admin.settings

data class AdminPlayerResponse(
    val id: Int,
    val name: String?,
    val description: String,
    val type: String?,
    val lastSeen: String?,
    val dynamicIp: Boolean,
    val autoControlEnabled: Boolean,
    val m3uBomEnabled: Boolean,
    val technology: String,
    val transcodeScheme: String,
    val transcodingIds: List<Int>,
)

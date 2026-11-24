package su.nnedition.kord.api.module.admin.settings

data class DatabaseSettingsResponse(
    val configType: String,
    val driver: String,
    val url: String,
    val username: String,
    val jndiName: String,
    val mysqlVarcharMaxlength: Int,
    val backupIntervalHours: Int,
    val backupRetentionCount: Int,
    val backuppable: Boolean,
    val passwordConfigured: Boolean,
)

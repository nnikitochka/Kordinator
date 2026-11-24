package su.nnedition.kord.api.module.admin.settings

data class UpdateDatabaseSettingsRequest(
    val configType: String,
    val driver: String = "",
    val url: String = "",
    val username: String = "",
    val jndiName: String = "",
    val password: String? = null,
    val clearPassword: Boolean = false,
    val mysqlVarcharMaxlength: Int = 0,
    val backupIntervalHours: Int,
    val backupRetentionCount: Int,
)

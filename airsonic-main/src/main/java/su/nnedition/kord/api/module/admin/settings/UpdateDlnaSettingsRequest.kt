package su.nnedition.kord.api.module.admin.settings

data class UpdateDlnaSettingsRequest(val enabled: Boolean, val serverName: String, val baseLanUrl: String = "")

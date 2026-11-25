package su.nnedition.kord.api.module.admin.settings

data class AdvancedSettingsResponse(
    val downloadLimit: Long, val uploadLimit: Long,
    val ldapUrl: String, val ldapSearchFilter: String, val ldapManagerDn: String,
    val smtpServer: String, val smtpEncryption: String, val smtpPort: String,
    val smtpUser: String, val smtpFrom: String, val recaptchaSiteKey: String,
    val brand: String, val ldapEnabled: Boolean, val ldapAutoShadowing: Boolean, val captchaEnabled: Boolean,
)

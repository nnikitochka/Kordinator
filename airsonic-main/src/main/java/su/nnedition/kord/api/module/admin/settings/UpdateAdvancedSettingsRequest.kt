package su.nnedition.kord.api.module.admin.settings

data class UpdateAdvancedSettingsRequest(
    val downloadLimit: Long, val uploadLimit: Long,
    val ldapUrl: String, val ldapSearchFilter: String, val ldapManagerDn: String,
    val smtpServer: String, val smtpEncryption: String, val smtpPort: String,
    val smtpUser: String, val smtpFrom: String, val recaptchaSiteKey: String,
    val ldapEnabled: Boolean, val ldapAutoShadowing: Boolean, val captchaEnabled: Boolean,
    val ldapManagerPassword: String? = null, val smtpPassword: String? = null, val recaptchaSecretKey: String? = null,
)

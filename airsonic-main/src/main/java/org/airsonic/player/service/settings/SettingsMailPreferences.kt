package org.airsonic.player.service.settings

import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory

/** Owns SMTP delivery and CAPTCHA provider settings. */
internal class SettingsMailPreferences(private val values: SettingsValueStore) {
    fun getSmtpServer(): String? = values.getString(KEY_SMTP_SERVER, null)
    fun setSmtpServer(server: String?) = values.setProperty(KEY_SMTP_SERVER, server)
    fun getSmtpPort(): String? = values.getString(KEY_SMTP_PORT, DEFAULT_SMTP_PORT)
    fun setSmtpPort(port: String?) = values.setProperty(KEY_SMTP_PORT, port)
    fun getSmtpEncryption(): String? = values.getString(KEY_SMTP_ENCRYPTION, DEFAULT_SMTP_ENCRYPTION)
    fun setSmtpEncryption(encryption: String?) = values.setProperty(KEY_SMTP_ENCRYPTION, encryption)
    fun getSmtpUser(): String? = values.getString(KEY_SMTP_USER, null)
    fun setSmtpUser(user: String?) = values.setProperty(KEY_SMTP_USER, user)

    fun getSmtpPassword(): String? {
        val password = values.getString(KEY_SMTP_PASSWORD, null)
        return try {
            StringUtil.utf8HexDecode(password)
        } catch (failure: Exception) {
            LOG.warn("Failed to decode Smtp password.", failure)
            password
        }
    }

    fun setSmtpPassword(password: String?) {
        val encoded = try {
            StringUtil.utf8HexEncode(password)
        } catch (failure: Exception) {
            LOG.warn("Failed to encode Smtp password.", failure)
            password
        }
        values.setProperty(KEY_SMTP_PASSWORD, encoded)
    }

    fun getSmtpFrom(): String? = values.getString(KEY_SMTP_FROM, DEFAULT_SMTP_FROM)
    fun setSmtpFrom(from: String?) = values.setProperty(KEY_SMTP_FROM, from)

    fun isCaptchaEnabled(): Boolean = values.getBoolean(KEY_CAPTCHA_ENABLED, false)
    fun setCaptchaEnabled(enabled: Boolean) = values.setProperty(KEY_CAPTCHA_ENABLED, enabled)
    fun getRecaptchaSiteKey(): String? = values.getString(KEY_RECAPTCHA_SITE_KEY, DEFAULT_RECAPTCHA_SITE_KEY)
    fun setRecaptchaSiteKey(key: String?) = values.setProperty(KEY_RECAPTCHA_SITE_KEY, key)
    fun getRecaptchaSecretKey(): String? = values.getString(KEY_RECAPTCHA_SECRET_KEY, DEFAULT_RECAPTCHA_SECRET_KEY)
    fun setRecaptchaSecretKey(key: String?) = values.setProperty(KEY_RECAPTCHA_SECRET_KEY, key)

    private companion object {
        val LOG = LoggerFactory.getLogger(SettingsMailPreferences::class.java)

        const val KEY_SMTP_SERVER = "SmtpServer"
        const val KEY_SMTP_ENCRYPTION = "SmtpEncryption"
        const val KEY_SMTP_PORT = "SmtpPort"
        const val KEY_SMTP_USER = "SmtpUser"
        const val KEY_SMTP_PASSWORD = "SmtpPassword"
        const val KEY_SMTP_FROM = "SmtpFrom"
        const val KEY_CAPTCHA_ENABLED = "CaptchaEnabled"
        const val KEY_RECAPTCHA_SITE_KEY = "ReCaptchaSiteKey"
        const val KEY_RECAPTCHA_SECRET_KEY = "ReCaptchaSecretKey"
        const val DEFAULT_SMTP_ENCRYPTION = "None"
        const val DEFAULT_SMTP_PORT = "25"
        const val DEFAULT_SMTP_FROM = "airsonic@airsonic.org"
        const val DEFAULT_RECAPTCHA_SITE_KEY = "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI"
        const val DEFAULT_RECAPTCHA_SECRET_KEY = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe"
    }
}

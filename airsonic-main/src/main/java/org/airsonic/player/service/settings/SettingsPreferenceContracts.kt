package org.airsonic.player.service.settings

import com.google.common.util.concurrent.RateLimiter
import java.util.Locale
import java.util.regex.Pattern
import org.airsonic.player.domain.Avatar
import org.airsonic.player.domain.CoverArtSource
import org.airsonic.player.domain.Theme
import org.airsonic.player.domain.UserSettings

interface SettingsCatalogAccess {
    fun getIndexString(): String
    fun setIndexString(value: String?)
    fun getIgnoredArticles(): String
    fun getIgnoredArticlesAsArray(): Array<String>
    fun setIgnoredArticles(value: String?)
    fun getUploadsFolder(): String
    fun setUploadsFolder(value: String?)
    fun getGenreSeparators(): String
    fun setGenreSeparators(value: String?)
    fun getShortcuts(): String
    fun getShortcutsAsArray(): Array<String>
    fun setShortcuts(value: String?)
    fun getPlaylistFolder(): String
    fun setPlaylistFolder(value: String?)
    fun getMusicFileTypes(): String
    fun setMusicFileTypes(value: String?)
    fun getMusicFileTypesSet(): Set<String>
    fun getVideoFileTypes(): String
    fun setVideoFileTypes(value: String?)
    fun getVideoFileTypesSet(): Set<String>
    fun getCoverArtFileTypes(): String
    fun setCoverArtFileTypes(value: String?)
    fun getCoverArtFileTypesSet(): Set<String>
    fun getCoverArtSource(): CoverArtSource
    fun setCoverArtSource(source: CoverArtSource)
    fun getCoverArtConcurrency(): Int
    fun setCoverArtConcurrency(value: Int?)
    fun getCoverArtQuality(): Int
    fun setCoverArtQuality(value: Int?)
}

interface SettingsOperationalAccess {
    fun getWelcomeTitle(): String?
    fun setWelcomeTitle(value: String?)
    fun getWelcomeSubtitle(): String?
    fun setWelcomeSubtitle(value: String?)
    fun getWelcomeMessage(): String?
    fun setWelcomeMessage(value: String?)
    fun getLoginMessage(): String?
    fun setLoginMessage(value: String?)
    fun getSessionDuration(): String
    fun setSessionDuration(value: String?)
    fun getIndexCreationInterval(): Int
    fun setIndexCreationInterval(value: Int)
    fun getIndexCreationHour(): Int
    fun setIndexCreationHour(value: Int)
    fun isFastCacheEnabled(): Boolean
    fun setFastCacheEnabled(value: Boolean)
    fun getFullScan(): Boolean
    fun setFullScan(value: Boolean?)
    fun getClearFullScanSettingAfterScan(): Boolean
    fun setClearFullScanSettingAfterScan(value: Boolean?)
    fun getTranscodeEstimateTimePadding(): Long
    fun setTranscodeEstimateTimePadding(value: Long?)
    fun getTranscodeEstimateBytePadding(): Long
    fun setTranscodeEstimateBytePadding(value: Long?)
    fun getDbBackupInterval(): Int
    fun setDbBackupInterval(value: Int)
    fun getDbBackupRetentionCount(): Int
    fun setDbBackupRetentionCount(value: Int)
    fun isGettingStartedEnabled(): Boolean
    fun setGettingStartedEnabled(value: Boolean)
    fun getSettingsChanged(): Long
    fun isOrganizeByFolderStructure(): Boolean
    fun setOrganizeByFolderStructure(value: Boolean)
    fun isSortAlbumsByYear(): Boolean
    fun setSortAlbumsByYear(value: Boolean)
    fun getIgnoreSymLinks(): Boolean
    fun setIgnoreSymLinks(value: Boolean)
    fun getExcludePatternString(): String?
    fun setExcludePatternString(value: String?)
    fun getExcludePattern(): Pattern?
}

interface SettingsRateLimitAccess {
    fun getDownloadBitrateLimit(): Long
    fun getDownloadBitrateLimiter(): RateLimiter
    fun setDownloadBitrateLimit(limit: Long)
    fun getUploadBitrateLimit(): Long
    fun getUploadBitrateLimiter(): RateLimiter
    fun setUploadBitrateLimit(limit: Long)
}

interface SettingsTranscodingAccess {
    fun getDownsamplingCommand(): String
    fun setDownsamplingCommand(command: String?)
    fun getHlsCommand(): String
    fun setHlsCommand(command: String?)
    fun getJukeboxCommand(): String
    fun setJukeboxCommand(command: String?)
    fun getVideoImageCommand(): String
    fun setVideoImageCommand(command: String?)
    fun getSubtitlesExtractionCommand(): String
    fun setSubtitlesExtractionCommand(command: String?)
}

interface SettingsLdapAccess {
    fun isLdapEnabled(): Boolean
    fun setLdapEnabled(enabled: Boolean)
    fun getLdapUrl(): String?
    fun setLdapUrl(url: String?)
    fun getLdapSearchFilter(): String
    fun setLdapSearchFilter(filter: String?)
    fun getLdapManagerDn(): String?
    fun setLdapManagerDn(dn: String?)
    fun getLdapManagerPassword(): String?
    fun setLdapManagerPassword(password: String?)
    fun isLdapAutoShadowing(): Boolean
    fun setLdapAutoShadowing(enabled: Boolean)
}

interface SettingsMailAccess {
    fun getSmtpServer(): String?
    fun setSmtpServer(server: String?)
    fun getSmtpPort(): String?
    fun setSmtpPort(port: String?)
    fun getSmtpEncryption(): String?
    fun setSmtpEncryption(encryption: String?)
    fun getSmtpUser(): String?
    fun setSmtpUser(user: String?)
    fun getSmtpPassword(): String?
    fun setSmtpPassword(password: String?)
    fun getSmtpFrom(): String?
    fun setSmtpFrom(from: String?)
    fun isCaptchaEnabled(): Boolean
    fun setCaptchaEnabled(enabled: Boolean)
    fun getRecaptchaSiteKey(): String?
    fun setRecaptchaSiteKey(key: String?)
    fun getRecaptchaSecretKey(): String?
    fun setRecaptchaSecretKey(key: String?)
}

interface SettingsDlnaAccess {
    fun isDlnaEnabled(): Boolean
    fun setDlnaEnabled(enabled: Boolean)
    fun getDlnaServerName(): String
    fun setDlnaServerName(name: String?)
    fun getDlnaServerId(): String?
    fun setDlnaServerId(id: String?)
    fun getDlnaBaseLANURL(): String?
    fun setDlnaBaseLANURL(url: String?)
    fun getUPnpPort(): Int
}

interface SettingsDatabaseAccess {
    fun getDatabaseDriver(): String?
    fun setDatabaseDriver(driver: String?)
    fun getDatabaseUrl(): String?
    fun setDatabaseUrl(url: String?)
    fun getDatabaseUsername(): String?
    fun setDatabaseUsername(username: String?)
    fun getDatabasePassword(): String?
    fun setDatabasePassword(password: String?)
    fun getDatabaseJNDIName(): String?
    fun setDatabaseJNDIName(name: String?)
    fun getDatabaseMysqlVarcharMaxlength(): Int
    fun setDatabaseMysqlVarcharMaxlength(maxlength: Int)
}

interface SettingsCredentialAccess {
    fun getJWTKey(): String?
    fun setJWTKey(key: String?)
    fun getEncryptionPassword(): String?
    fun setEncryptionPassword(password: String?)
    fun getEncryptionSalt(): String?
    fun setEncryptionSalt(salt: String?)
    fun getDecodablePasswordEncoder(): String
    fun setDecodablePasswordEncoder(encoder: String?)
    fun getNonDecodablePasswordEncoder(): String
    fun setNonDecodablePasswordEncoder(encoder: String?)
    fun getPreferNonDecodablePasswords(): Boolean
    fun setPreferNonDecodablePasswords(preference: Boolean)
}

interface SettingsUserAccess {
    fun getUserSettings(username: String): UserSettings
    fun updateUserSettings(settings: UserSettings?)
    fun getAllSystemAvatars(): List<Avatar>
    fun getSystemAvatar(id: Int): Avatar?
    fun getCustomAvatar(username: String): Avatar?
    fun setCustomAvatar(avatar: Avatar?, username: String)
    fun setUserDao(userDao: org.airsonic.player.dao.UserDao)
    fun setAvatarDao(avatarDao: org.airsonic.player.dao.AvatarDao)
}

interface SettingsPresentationAccess {
    fun getLocale(): Locale
    fun setLocale(locale: Locale)
    fun getThemeId(): String
    fun setThemeId(themeId: String?)
    fun getAvailableThemes(): Array<Theme>
    fun getAvailableLocales(): Array<Locale>
    fun getBrand(): String
}

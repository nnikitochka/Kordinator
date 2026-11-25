package org.airsonic.player.service

import java.util.Optional
import java.util.function.Function
import java.util.regex.Pattern
import org.apache.commons.lang3.StringUtils

/** Owns indexing, scan, backup, notice, and library organization settings. */
internal class SettingsOperationalPreferences(private val values: SettingsValueStore) {
    fun getWelcomeTitle(): String? = trimmed(KEY_WELCOME_TITLE, DEFAULT_WELCOME_TITLE)
    fun setWelcomeTitle(value: String?) = values.setProperty(KEY_WELCOME_TITLE, value)
    fun getWelcomeSubtitle(): String? = trimmed(KEY_WELCOME_SUBTITLE, null)
    fun setWelcomeSubtitle(value: String?) = values.setProperty(KEY_WELCOME_SUBTITLE, value)
    fun getWelcomeMessage(): String? = trimmed(KEY_WELCOME_MESSAGE, DEFAULT_WELCOME_MESSAGE)
    fun setWelcomeMessage(value: String?) = values.setProperty(KEY_WELCOME_MESSAGE, value)
    fun getLoginMessage(): String? = trimmed(KEY_LOGIN_MESSAGE, null)
    fun setLoginMessage(value: String?) = values.setProperty(KEY_LOGIN_MESSAGE, value)
    fun getSessionDuration(): String = string(KEY_SESSION_DURATION, DEFAULT_SESSION_DURATION)
    fun setSessionDuration(value: String?) = values.setProperty(KEY_SESSION_DURATION, value)

    fun getIndexCreationInterval(): Int = values.getInt(KEY_INDEX_CREATION_INTERVAL, 1)
    fun setIndexCreationInterval(value: Int) = values.setProperty(KEY_INDEX_CREATION_INTERVAL, value)
    fun getIndexCreationHour(): Int = values.getInt(KEY_INDEX_CREATION_HOUR, 3)
    fun setIndexCreationHour(value: Int) = values.setProperty(KEY_INDEX_CREATION_HOUR, value)
    fun isFastCacheEnabled(): Boolean = values.getBoolean(KEY_FAST_CACHE_ENABLED, false)
    fun setFastCacheEnabled(value: Boolean) = values.setProperty(KEY_FAST_CACHE_ENABLED, value)
    fun getFullScan(): Boolean = values.getBoolean(KEY_FULL_SCAN, false)
    fun setFullScan(value: Boolean?) = values.setProperty(KEY_FULL_SCAN, value)
    fun getClearFullScanSettingAfterScan(): Boolean = values.getBoolean(KEY_CLEAR_FULL_SCAN_AFTER_SCAN, false)
    fun setClearFullScanSettingAfterScan(value: Boolean?) = values.setProperty(KEY_CLEAR_FULL_SCAN_AFTER_SCAN, value)

    fun getTranscodeEstimateTimePadding(): Long = values.getLong(KEY_TRANSCODE_TIME_PADDING, 2_000)
    fun setTranscodeEstimateTimePadding(value: Long?) = values.setProperty(KEY_TRANSCODE_TIME_PADDING, value)
    fun getTranscodeEstimateBytePadding(): Long = values.getLong(KEY_TRANSCODE_BYTE_PADDING, 0)
    fun setTranscodeEstimateBytePadding(value: Long?) = values.setProperty(KEY_TRANSCODE_BYTE_PADDING, value)
    fun getDbBackupInterval(): Int = values.getInt(KEY_DB_BACKUP_INTERVAL, -1)
    fun setDbBackupInterval(value: Int) = values.setProperty(KEY_DB_BACKUP_INTERVAL, value)
    fun getDbBackupRetentionCount(): Int = values.getInt(KEY_DB_BACKUP_RETENTION_COUNT, 2)
    fun setDbBackupRetentionCount(value: Int) = values.setProperty(KEY_DB_BACKUP_RETENTION_COUNT, value)
    fun getSettingsChanged(): Long = values.getLong(KEY_SETTINGS_CHANGED, 0)
    fun setSettingsChanged(value: Long) = values.setProperty(KEY_SETTINGS_CHANGED, value)

    fun isGettingStartedEnabled(): Boolean = values.getBoolean(KEY_GETTING_STARTED_ENABLED, true)
    fun setGettingStartedEnabled(value: Boolean) = values.setProperty(KEY_GETTING_STARTED_ENABLED, value)
    fun isOrganizeByFolderStructure(): Boolean = values.getBoolean(KEY_ORGANIZE_BY_FOLDER_STRUCTURE, true)
    fun setOrganizeByFolderStructure(value: Boolean) = values.setProperty(KEY_ORGANIZE_BY_FOLDER_STRUCTURE, value)
    fun isSortAlbumsByYear(): Boolean = values.getBoolean(KEY_SORT_ALBUMS_BY_YEAR, true)
    fun setSortAlbumsByYear(value: Boolean) = values.setProperty(KEY_SORT_ALBUMS_BY_YEAR, value)
    fun getIgnoreSymLinks(): Boolean = values.getBoolean(KEY_IGNORE_SYMLINKS, false)
    fun setIgnoreSymLinks(value: Boolean) = values.setProperty(KEY_IGNORE_SYMLINKS, value)
    fun getExcludePatternString(): String? = values.getString(KEY_EXCLUDE_PATTERN, null)
    fun setExcludePatternString(value: String?) {
        values.setProperty(KEY_EXCLUDE_PATTERN, value)
        values.invalidateDerived(KEY_EXCLUDE_PATTERN)
    }

    fun getExcludePattern(): Pattern? =
        (values.derived(KEY_EXCLUDE_PATTERN, Function {
            Optional.ofNullable(getExcludePatternString())
                .map(StringUtils::trimToNull)
                .map(Pattern::compile)
        }) as Optional<*>).orElse(null) as Pattern?

    private fun trimmed(key: String, defaultValue: String?): String? =
        StringUtils.trimToNull(values.getString(key, defaultValue))

    private fun string(key: String, defaultValue: String): String = requireNotNull(values.getString(key, defaultValue))

    private companion object {
        const val KEY_WELCOME_TITLE = "WelcomeTitle"
        const val KEY_WELCOME_SUBTITLE = "WelcomeSubtitle"
        const val KEY_WELCOME_MESSAGE = "WelcomeMessage2"
        const val KEY_LOGIN_MESSAGE = "LoginMessage"
        const val KEY_SESSION_DURATION = "server.servlet.session.timeout"
        const val KEY_INDEX_CREATION_INTERVAL = "IndexCreationInterval"
        const val KEY_INDEX_CREATION_HOUR = "IndexCreationHour"
        const val KEY_FAST_CACHE_ENABLED = "FastCacheEnabled"
        const val KEY_FULL_SCAN = "FullScan"
        const val KEY_CLEAR_FULL_SCAN_AFTER_SCAN = "ClearFullScanSettingAfterScan"
        const val KEY_TRANSCODE_TIME_PADDING = "TranscodeEstimateTimePadding"
        const val KEY_TRANSCODE_BYTE_PADDING = "TranscodeEstimateBytePadding"
        const val KEY_DB_BACKUP_INTERVAL = "DbBackupUpdateInterval"
        const val KEY_DB_BACKUP_RETENTION_COUNT = "DbBackupRetentionCount"
        const val KEY_SETTINGS_CHANGED = "SettingsChanged"
        const val KEY_GETTING_STARTED_ENABLED = "GettingStartedEnabled"
        const val KEY_ORGANIZE_BY_FOLDER_STRUCTURE = "OrganizeByFolderStructure"
        const val KEY_SORT_ALBUMS_BY_YEAR = "SortAlbumsByYear"
        const val KEY_IGNORE_SYMLINKS = "IgnoreSymLinks"
        const val KEY_EXCLUDE_PATTERN = "ExcludePattern"
        const val DEFAULT_WELCOME_TITLE = "Welcome to Airsonic!"
        const val DEFAULT_SESSION_DURATION = "30m"
        const val DEFAULT_WELCOME_MESSAGE = "__Welcome to Airsonic!__\n" +
            "\\\\ \\\\\n" +
            "Airsonic is a free, web-based media streamer, providing ubiquitous access to your music. \n" +
            "\\\\ \\\\\n" +
            "Use it to share your music with friends, or to listen to your own music while at work. You can stream to multiple " +
            "players simultaneously, for instance to one player in your kitchen and another in your living room.\n" +
            "\\\\ \\\\\n" +
            "To change or remove this message, log in with administrator rights and go to <a href='settings.view'>Settings</a> > <a href='generalSettings.view'>General</a>."
    }
}

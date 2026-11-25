package su.nnedition.kord.service.settings

import java.util.Optional
import java.util.function.Function
import java.util.regex.Pattern
import org.apache.commons.lang3.StringUtils

/** Owns indexing, scan, backup, notice, and library organization settings. */
internal class SettingsOperationalPreferences(private val values: SettingsValueStore) : SettingsOperationalAccess {
    override fun getWelcomeTitle(): String? = trimmed(KEY_WELCOME_TITLE, DEFAULT_WELCOME_TITLE)
    override fun setWelcomeTitle(value: String?) = values.setProperty(KEY_WELCOME_TITLE, value)
    override fun getWelcomeSubtitle(): String? = trimmed(KEY_WELCOME_SUBTITLE, null)
    override fun setWelcomeSubtitle(value: String?) = values.setProperty(KEY_WELCOME_SUBTITLE, value)
    override fun getWelcomeMessage(): String? = trimmed(KEY_WELCOME_MESSAGE, DEFAULT_WELCOME_MESSAGE)
    override fun setWelcomeMessage(value: String?) = values.setProperty(KEY_WELCOME_MESSAGE, value)
    override fun getLoginMessage(): String? = trimmed(KEY_LOGIN_MESSAGE, null)
    override fun setLoginMessage(value: String?) = values.setProperty(KEY_LOGIN_MESSAGE, value)
    override fun getSessionDuration(): String = string(KEY_SESSION_DURATION, DEFAULT_SESSION_DURATION)
    override fun setSessionDuration(value: String?) = values.setProperty(KEY_SESSION_DURATION, value)

    override fun getIndexCreationInterval(): Int = values.getInt(KEY_INDEX_CREATION_INTERVAL, 1)
    override fun setIndexCreationInterval(value: Int) = values.setProperty(KEY_INDEX_CREATION_INTERVAL, value)
    override fun getIndexCreationHour(): Int = values.getInt(KEY_INDEX_CREATION_HOUR, 3)
    override fun setIndexCreationHour(value: Int) = values.setProperty(KEY_INDEX_CREATION_HOUR, value)
    override fun isFastCacheEnabled(): Boolean = values.getBoolean(KEY_FAST_CACHE_ENABLED, false)
    override fun setFastCacheEnabled(value: Boolean) = values.setProperty(KEY_FAST_CACHE_ENABLED, value)
    override fun getFullScan(): Boolean = values.getBoolean(KEY_FULL_SCAN, false)
    override fun setFullScan(value: Boolean?) = values.setProperty(KEY_FULL_SCAN, value)
    override fun getClearFullScanSettingAfterScan(): Boolean = values.getBoolean(KEY_CLEAR_FULL_SCAN_AFTER_SCAN, false)
    override fun setClearFullScanSettingAfterScan(value: Boolean?) = values.setProperty(KEY_CLEAR_FULL_SCAN_AFTER_SCAN, value)

    override fun getTranscodeEstimateTimePadding(): Long = values.getLong(KEY_TRANSCODE_TIME_PADDING, 2_000)
    override fun setTranscodeEstimateTimePadding(value: Long?) = values.setProperty(KEY_TRANSCODE_TIME_PADDING, value)
    override fun getTranscodeEstimateBytePadding(): Long = values.getLong(KEY_TRANSCODE_BYTE_PADDING, 0)
    override fun setTranscodeEstimateBytePadding(value: Long?) = values.setProperty(KEY_TRANSCODE_BYTE_PADDING, value)
    override fun getDbBackupInterval(): Int = values.getInt(KEY_DB_BACKUP_INTERVAL, -1)
    override fun setDbBackupInterval(value: Int) = values.setProperty(KEY_DB_BACKUP_INTERVAL, value)
    override fun getDbBackupRetentionCount(): Int = values.getInt(KEY_DB_BACKUP_RETENTION_COUNT, 2)
    override fun setDbBackupRetentionCount(value: Int) = values.setProperty(KEY_DB_BACKUP_RETENTION_COUNT, value)
    override fun getSettingsChanged(): Long = values.getLong(KEY_SETTINGS_CHANGED, 0)
    fun setSettingsChanged(value: Long) = values.setProperty(KEY_SETTINGS_CHANGED, value)

    override fun isGettingStartedEnabled(): Boolean = values.getBoolean(KEY_GETTING_STARTED_ENABLED, true)
    override fun setGettingStartedEnabled(value: Boolean) = values.setProperty(KEY_GETTING_STARTED_ENABLED, value)
    override fun isOrganizeByFolderStructure(): Boolean = values.getBoolean(KEY_ORGANIZE_BY_FOLDER_STRUCTURE, true)
    override fun setOrganizeByFolderStructure(value: Boolean) = values.setProperty(KEY_ORGANIZE_BY_FOLDER_STRUCTURE, value)
    override fun isSortAlbumsByYear(): Boolean = values.getBoolean(KEY_SORT_ALBUMS_BY_YEAR, true)
    override fun setSortAlbumsByYear(value: Boolean) = values.setProperty(KEY_SORT_ALBUMS_BY_YEAR, value)
    override fun getIgnoreSymLinks(): Boolean = values.getBoolean(KEY_IGNORE_SYMLINKS, false)
    override fun setIgnoreSymLinks(value: Boolean) = values.setProperty(KEY_IGNORE_SYMLINKS, value)
    override fun getExcludePatternString(): String? = values.getString(KEY_EXCLUDE_PATTERN, null)
    override fun setExcludePatternString(value: String?) {
        values.setProperty(KEY_EXCLUDE_PATTERN, value)
        values.invalidateDerived(KEY_EXCLUDE_PATTERN)
    }

    override fun getExcludePattern(): Pattern? =
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
        const val DEFAULT_WELCOME_TITLE = "Welcome to Kordinator!"
        const val DEFAULT_SESSION_DURATION = "30m"
        const val DEFAULT_WELCOME_MESSAGE = "__Welcome to Kordinator!__\n" +
            "\\\\ \\\\\n" +
            "Kordinator is a free, web-based media streamer, providing ubiquitous access to your music. \n" +
            "\\\\ \\\\\n" +
            "Use it to share your music with friends, or to listen to your own music while at work. You can stream to multiple " +
            "players simultaneously, for instance to one player in your kitchen and another in your living room.\n" +
            "\\\\ \\\\\n" +
            "To change or remove this message, log in with administrator rights and go to <a href='settings.view'>Settings</a> > <a href='generalSettings.view'>General</a>."
    }
}

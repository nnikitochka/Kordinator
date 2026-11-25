package org.airsonic.player.service

import com.google.common.util.concurrent.RateLimiter
import java.nio.file.Path
import java.util.HashMap
import java.util.Locale
import java.util.function.Function
import java.util.function.Supplier
import java.util.regex.Pattern
import javax.cache.CacheManager
import org.airsonic.player.dao.AvatarDao
import org.airsonic.player.dao.UserDao
import org.airsonic.player.domain.Avatar
import org.airsonic.player.domain.CoverArtSource
import org.airsonic.player.domain.Theme
import org.airsonic.player.domain.UserSettings
import org.airsonic.player.runtime.RuntimeConfiguration
import org.airsonic.player.service.settings.SettingsCatalogAccess
import org.airsonic.player.service.settings.SettingsCredentialAccess
import org.airsonic.player.service.settings.SettingsDatabaseAccess
import org.airsonic.player.service.settings.SettingsDlnaAccess
import org.airsonic.player.service.settings.SettingsLdapAccess
import org.airsonic.player.service.settings.SettingsMailAccess
import org.airsonic.player.service.settings.SettingsOperationalAccess
import org.airsonic.player.service.settings.SettingsPresentationAccess
import org.airsonic.player.service.settings.SettingsRateLimitAccess
import org.airsonic.player.service.settings.SettingsServiceComponents
import org.airsonic.player.service.settings.SettingsTranscodingAccess
import org.airsonic.player.service.settings.SettingsUserAccess
import org.airsonic.player.service.settings.SettingsValueStore
import org.airsonic.player.service.settings.SettingsContextExpressionResolver
import org.airsonic.player.util.Util
import org.slf4j.LoggerFactory

/** Persistent storage of application settings, composed from focused preference components. */
class SettingsService private constructor(
    private val components: SettingsServiceComponents,
) : SettingsCatalogAccess by components.catalogPreferences,
    SettingsOperationalAccess by components.operationalPreferences,
    SettingsRateLimitAccess by components.rateLimitPreferences,
    SettingsTranscodingAccess by components.transcodingPreferences,
    SettingsLdapAccess by components.ldapPreferences,
    SettingsMailAccess by components.mailPreferences,
    SettingsDlnaAccess by components.dlnaPreferences,
    SettingsDatabaseAccess by components.databasePreferences,
    SettingsCredentialAccess by components.credentialPreferences,
    SettingsUserAccess by components.userPreferences,
    SettingsPresentationAccess by components.presentationPreferences {

    private val values: SettingsValueStore = components.valueStore

    constructor(
        userDao: UserDao,
        avatarDao: AvatarDao,
        configuration: RuntimeConfiguration,
        cacheManager: CacheManager?,
        properties: ConfigurationPropertiesService,
    ) : this(
        SettingsServiceComponents.create(
            userDao,
            avatarDao,
            Function { key -> configuration.getProperty(key) },
            cacheManager,
            properties,
        ),
    )

    override fun getUPnpPort(): Int = components.dlnaPreferences.getUPnpPort()

    fun getLogFile(): String = requireNotNull(values.getString(LOG_FILE_NAME_PROPERTY, getDefaultLogFile()))

    fun init() {
        LOG.info(
            "Java: {}, OS: {}, Memory (max bytes): {}",
            Runtime.version(),
            System.getProperty("os.name"),
            Runtime.getRuntime().maxMemory(),
        )
    }

    fun save() {
        components.operationalPreferences.setSettingsChanged(System.currentTimeMillis())
        components.properties.save()
    }

    fun buildSpelContext(): MutableMap<String, Any> = HashMap<String, Any>().apply {
        put("AIRSONIC_HOME", getAirsonicHome())
        put("DEFAULT_PLAYLIST_FOLDER", getPlaylistFolder())
        put("DEFAULT_MUSIC_FOLDER", Util.getDefaultMusicFolder())
    }

    fun resolveContextualString(value: String, contextSupplier: Supplier<Map<String, Any>>): String =
        SettingsContextExpressionResolver.resolve(value, contextSupplier)

    fun getRememberMeKey(): String? = values.getString(KEY_REMEMBER_ME_KEY, null)

    fun setPropertyLookup(propertyLookup: Function<String, String?>) {
        values.setPropertyLookup(requireNotNull(propertyLookup))
    }

    fun resetDatabaseToDefault() {
        setDatabaseDriver(DEFAULT_DATABASE_DRIVER)
        setDatabasePassword(DEFAULT_DATABASE_PASSWORD)
        setDatabaseUrl(DEFAULT_DATABASE_URL)
        setDatabaseUsername(DEFAULT_DATABASE_USERNAME)
        setDatabaseJNDIName(DEFAULT_DATABASE_JNDI_NAME)
        setDatabaseMysqlVarcharMaxlength(DEFAULT_DATABASE_MYSQL_VARCHAR_MAXLENGTH)
    }

    fun getPlaylistExportFormat(): String =
        requireNotNull(values.getString(KEY_EXPORT_PLAYLIST_FORMAT, DEFAULT_EXPORT_PLAYLIST_FORMAT))

    companion object {
        private val LOG = LoggerFactory.getLogger(SettingsService::class.java)

        @JvmField val KEY_DATABASE_URL = RuntimeConfiguration.DATABASE_URL_KEY
        @JvmField val KEY_DATABASE_USERNAME = RuntimeConfiguration.DATABASE_USERNAME_KEY
        @JvmField val KEY_DATABASE_PASSWORD = RuntimeConfiguration.DATABASE_PASSWORD_KEY
        @JvmField val KEY_DATABASE_JNDI_NAME = RuntimeConfiguration.DATABASE_JNDI_NAME_KEY
        @JvmField val KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS = "PropertiesFileRetainObsoleteKeys"

        private const val KEY_REMEMBER_ME_KEY = "RememberMeKey"
        private const val KEY_EXPORT_PLAYLIST_FORMAT = "PlaylistExportFormat"
        private const val DEFAULT_EXPORT_PLAYLIST_FORMAT = "m3u"
        private const val LOG_FILE_NAME_PROPERTY = "logging.file.name"
        private const val DEFAULT_DATABASE_MYSQL_VARCHAR_MAXLENGTH = 384
        private const val KEY_DATABASE_MYSQL_VARCHAR_MAXLENGTH = RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY

        private val DEFAULT_DATABASE_DRIVER: String? = null
        private val DEFAULT_DATABASE_URL: String? = null
        private val DEFAULT_DATABASE_USERNAME: String? = null
        private val DEFAULT_DATABASE_PASSWORD: String? = null
        private val DEFAULT_DATABASE_JNDI_NAME: String? = null

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun getMigratedPropertyKeys(): Map<String, String> =
            SettingsPropertyMigration.getMigratedPropertyKeys() as Map<String, String>

        @JvmStatic
        fun migrateKeys(
            keyMaps: Map<String, String>,
            propertyLookup: Function<String, Any?>,
            sourceName: String,
            migrated: MutableMap<String, Any>,
        ) {
            @Suppress("UNCHECKED_CAST")
            SettingsPropertyMigration.migrateKeys(
                keyMaps as Map<String, String?>,
                propertyLookup,
                sourceName,
                migrated as MutableMap<String, Any?>,
            )
        }

        @JvmStatic
        fun migratePropFileKeys(keyMaps: Map<String, String>, properties: ConfigurationPropertiesService) {
            @Suppress("UNCHECKED_CAST")
            SettingsPropertyMigration.migratePropertyFileKeys(keyMaps as Map<String, String?>, properties)
        }

        @JvmStatic
        fun setDefaultConstants(
            propertyLookup: Function<String, String?>,
            defaultConstants: MutableMap<String, Any>,
        ) {
            @Suppress("UNCHECKED_CAST")
            SettingsPropertyMigration.setDefaultConstants(
                propertyLookup,
                defaultConstants as MutableMap<String, Any?>,
            )
        }

        @JvmStatic fun getAirsonicHome(): Path = SettingsEnvironment.getAirsonicHome()
        @JvmStatic fun getTranscodeDirectory(): Path = SettingsEnvironment.getTranscodeDirectory()
        @JvmStatic fun resolveTranscodeExecutable(executable: String, defaultValue: String?): String? =
            SettingsEnvironment.resolveTranscodeExecutable(executable, defaultValue)
        @JvmStatic fun getDefaultJDBCUrl(): String = SettingsEnvironment.getDefaultJDBCUrl()
        @JvmStatic fun getDefaultJDBCUsername(): String = SettingsEnvironment.getDefaultJDBCUsername()
        @JvmStatic fun getDefaultJDBCPassword(): String = SettingsEnvironment.getDefaultJDBCPassword()
        @JvmStatic fun getDefaultLogFile(): String = SettingsEnvironment.getDefaultLogFile()
        @JvmStatic fun getPropertyFile(): Path = SettingsEnvironment.getPropertyFile()
        @JvmStatic fun isDevelopmentMode(): Boolean = System.getProperty("airsonic.development") != null
    }
}

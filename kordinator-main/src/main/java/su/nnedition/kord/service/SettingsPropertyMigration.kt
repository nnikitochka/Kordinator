package su.nnedition.kord.service

import org.apache.commons.lang3.StringUtils
import su.nnedition.kord.runtime.RuntimeConfiguration
import su.nnedition.kord.util.Util
import org.slf4j.LoggerFactory
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.function.Function

/** Property key migration and default property setup used during application startup. */
object SettingsPropertyMigration {
    private val log = LoggerFactory.getLogger(SettingsPropertyMigration::class.java)

    private const val KEY_REMEMBER_ME_KEY = "RememberMeKey"
    private const val KEY_DATABASE_DRIVER = RuntimeConfiguration.DATABASE_DRIVER_KEY
    private const val KEY_DATABASE_URL = RuntimeConfiguration.DATABASE_URL_KEY
    private const val KEY_DATABASE_USERNAME = RuntimeConfiguration.DATABASE_USERNAME_KEY
    private const val KEY_DATABASE_PASSWORD = RuntimeConfiguration.DATABASE_PASSWORD_KEY
    private const val KEY_DATABASE_JNDI_NAME = RuntimeConfiguration.DATABASE_JNDI_NAME_KEY
    private const val KEY_DATABASE_MIGRATION_ROLLBACK_FILE = RuntimeConfiguration.DATABASE_MIGRATION_ROLLBACK_FILE_KEY
    private const val KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH =
        RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY
    private const val KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER =
        RuntimeConfiguration.DATABASE_MIGRATION_DEFAULT_MUSIC_FOLDER_KEY
    private const val KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS = "PropertiesFileRetainObsoleteKeys"
    private const val LOG_FILE_NAME_PROPERTY = "logging.file.name"

    private val obsoleteKeys = listOf(
        "PortForwardingPublicPort", "PortForwardingLocalPort", "DownsamplingCommand", "DownsamplingCommand2",
        "DownsamplingCommand3", "AutoCoverBatch", "MusicMask", "VideoMask", "CoverArtMask", "HlsCommand",
        "HlsCommand2", "HlsCommand3", "JukeboxCommand", "CoverArtFileTypes", "UrlRedirectCustomHost",
        "CoverArtLimit", "StreamPort", "PortForwardingEnabled", "RewriteUrl", "UrlRedirectCustomUrl",
        "UrlRedirectContextPath", "UrlRedirectFrom", "UrlRedirectionEnabled", "UrlRedirectType", "Port",
        "HttpsPort", "MediaLibraryStatistics", "LastScanned", "database.config.type", "DatabaseConfigType",
        "database.usertable.quote", "DatabaseUsertableQuote", "spring.liquibase.parameters.userTableQuote",
    )

    @JvmStatic
    fun getMigratedPropertyKeys(): Map<String, String?> = linkedMapOf<String, String?>().apply {
        obsoleteKeys.forEach { put(it, null) }

        put("database.config.embed.driver", "DatabaseConfigEmbedDriver")
        put("DatabaseConfigEmbedDriver", KEY_DATABASE_DRIVER)
        put("spring.datasource.driver-class-name", KEY_DATABASE_DRIVER)

        put("database.config.embed.url", "DatabaseConfigEmbedUrl")
        put("DatabaseConfigEmbedUrl", KEY_DATABASE_URL)
        put("spring.datasource.url", KEY_DATABASE_URL)

        put("database.config.embed.username", "DatabaseConfigEmbedUsername")
        put("DatabaseConfigEmbedUsername", KEY_DATABASE_USERNAME)
        put("spring.datasource.username", KEY_DATABASE_USERNAME)

        put("database.config.embed.password", "DatabaseConfigEmbedPassword")
        put("DatabaseConfigEmbedPassword", KEY_DATABASE_PASSWORD)
        put("spring.datasource.password", KEY_DATABASE_PASSWORD)

        put("database.config.jndi.name", "DatabaseConfigJNDIName")
        put("DatabaseConfigJNDIName", KEY_DATABASE_JNDI_NAME)
        put("spring.datasource.jndi-name", KEY_DATABASE_JNDI_NAME)

        put("spring.liquibase.rollback-file", KEY_DATABASE_MIGRATION_ROLLBACK_FILE)
        put("spring.liquibase.parameters.mysqlVarcharLimit", KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH)
        put("spring.liquibase.parameters.defaultMusicFolder", KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER)
        put("spring.liquibase.enabled", RuntimeConfiguration.DATABASE_MIGRATION_ENABLED_KEY)
        put("spring.liquibase.change-log", RuntimeConfiguration.DATABASE_MIGRATION_CHANGELOG_KEY)
        put("spring.liquibase.contexts", RuntimeConfiguration.DATABASE_MIGRATION_CONTEXTS_KEY)
        put("spring.liquibase.label-filter", RuntimeConfiguration.DATABASE_MIGRATION_LABEL_FILTER_KEY)

        put("database.varchar.maxlength", "DatabaseMysqlMaxlength")
        put("DatabaseMysqlMaxlength", KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH)
        put("kordinator.rememberMeKey", KEY_REMEMBER_ME_KEY)
        put("IgnoreFileTimestamps", "FullScan")
    }

    @JvmStatic
    fun migrateKeys(
        keyMaps: Map<String, String?>,
        propertyLookup: Function<String, Any?>,
        sourceName: String,
        migrated: MutableMap<String, Any?>,
    ) {
        val pending = HashMap<String, Any?>()
        // The map is intentionally processed serially: a key may feed a later mapping.
        keyMaps.forEach { (oldKey, newKey) ->
            if (newKey == null) return@forEach
            val oldValue = propertyLookup.apply(oldKey)?.toString()
            val pendingOldValue = pending.getOrDefault(oldKey, "").toString()
            if (StringUtils.isBlank(oldValue ?: pendingOldValue)) return@forEach

            val newValue = propertyLookup.apply(newKey)?.toString()
            val pendingNewValue = pending.getOrDefault(newKey, "").toString()
            if (!StringUtils.isBlank(newValue ?: pendingNewValue)) return@forEach

            val value = propertyLookup.apply(oldKey) ?: pending[oldKey]
            pending[newKey] = value
            if (migrated.containsKey(newKey)) {
                log.info("Skipping migrating property [{}] to [{}] in {} (already migrated)", oldKey, newKey, sourceName)
            } else {
                log.info("Migrating property [{}] to [{}] in {}", oldKey, newKey, sourceName)
                migrated[newKey] = value
            }
        }
    }

    @JvmStatic
    fun migratePropertyFileKeys(keyMaps: Map<String, String?>, properties: ConfigurationPropertiesService) {
        val retainObsoleteKeys = (properties.getProperty(KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS) as String?)
            ?.let(java.lang.Boolean::valueOf) ?: true

        keyMaps.forEach { (oldKey, newKey) ->
            if (!properties.containsKey(oldKey)) return@forEach
            if (newKey != null && !properties.containsKey(newKey)) {
                log.info("Migrating property [{}] to [{}] in properties file", oldKey, newKey)
                properties.setProperty(newKey, properties.getProperty(oldKey))
            }
            if (!retainObsoleteKeys) {
                log.info("Removing obsolete property [{}] in properties file", oldKey)
                properties.clearProperty(oldKey)
            }
        }

        properties.save()
    }

    @JvmStatic
    fun setDefaultConstants(propertyLookup: Function<String, String?>, defaultConstants: MutableMap<String, Any?>) {
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_URL))) {
            defaultConstants[KEY_DATABASE_URL] = SettingsService.getDefaultJDBCUrl()
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_USERNAME))) {
            defaultConstants[KEY_DATABASE_USERNAME] = SettingsService.getDefaultJDBCUsername()
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_PASSWORD))) {
            defaultConstants[KEY_DATABASE_PASSWORD] = SettingsService.getDefaultJDBCPassword()
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_MIGRATION_ROLLBACK_FILE))) {
            defaultConstants[KEY_DATABASE_MIGRATION_ROLLBACK_FILE] =
                SettingsService.getKordinatorHome().toAbsolutePath().resolve("rollback.sql").toFile()
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH))) {
            defaultConstants[KEY_DATABASE_MIGRATION_PARAMETER_MYSQL_VARCHAR_MAXLENGTH] = "384"
        }
        if (StringUtils.isBlank(propertyLookup.apply(KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER))) {
            defaultConstants[KEY_DATABASE_MIGRATION_PARAMETER_DEFAULT_MUSIC_FOLDER] = Util.getDefaultMusicFolder()
        }
        if (StringUtils.isBlank(propertyLookup.apply(LOG_FILE_NAME_PROPERTY))) {
            defaultConstants[LOG_FILE_NAME_PROPERTY] = SettingsService.getDefaultLogFile()
        }
    }
}

package su.nnedition.kord.service.settings

import javax.cache.Cache
import javax.cache.CacheManager
import su.nnedition.kord.dao.AvatarDao
import su.nnedition.kord.dao.UserDao
import su.nnedition.kord.domain.UserSettings
import su.nnedition.kord.service.ConfigurationPropertiesService
import su.nnedition.kord.runtime.RuntimeConfiguration
import java.util.function.Function

/** Creates the shared settings store and its domain-specific preference adapters. */
internal class SettingsServiceComponents private constructor(
    val properties: ConfigurationPropertiesService,
    val valueStore: SettingsValueStore,
    val userPreferences: SettingsUserPreferences,
    val presentationPreferences: SettingsPresentationPreferences,
    val catalogPreferences: SettingsCatalogPreferences,
    val operationalPreferences: SettingsOperationalPreferences,
    val rateLimitPreferences: SettingsRateLimitPreferences,
    val ldapPreferences: SettingsLdapPreferences,
    val mailPreferences: SettingsMailPreferences,
    val dlnaPreferences: SettingsDlnaPreferences,
    val credentialPreferences: SettingsCredentialPreferences,
    val databasePreferences: SettingsDatabasePreferences,
    val transcodingPreferences: SettingsTranscodingPreferences,
) {
    companion object {
        fun create(
            userDao: UserDao,
            avatarDao: AvatarDao,
            propertyLookup: Function<String, String?>,
            cacheManager: CacheManager?,
            properties: ConfigurationPropertiesService,
        ): SettingsServiceComponents {
            val propertyService = requireNotNull(properties)
            val values = SettingsValueStore(propertyService, requireNotNull(propertyLookup))
            val userCache: Cache<String, UserSettings>? = cacheManager?.getCache(
                "userSettingsCache",
                String::class.java,
                UserSettings::class.java,
            )
            return SettingsServiceComponents(
                propertyService,
                values,
                SettingsUserPreferences(userDao, avatarDao, userCache),
                SettingsPresentationPreferences(values),
                SettingsCatalogPreferences(values),
                SettingsOperationalPreferences(values),
                SettingsRateLimitPreferences(values),
                SettingsLdapPreferences(values),
                SettingsMailPreferences(values),
                SettingsDlnaPreferences(values),
                SettingsCredentialPreferences(values),
                SettingsDatabasePreferences(values),
                SettingsTranscodingPreferences(values),
            )
        }
    }
}

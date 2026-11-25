package org.airsonic.player.service.settings

import org.airsonic.player.service.ConfigurationPropertiesService
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/** Reads, writes, and caches configured values shared by [org.airsonic.player.service.SettingsService]. */
internal class SettingsValueStore(
    private val properties: ConfigurationPropertiesService,
    propertyLookup: Function<String, String?>,
) {
    private var propertyLookup: Function<String, String?> = propertyLookup
    private val settingsCache = ConcurrentHashMap<String, Any>()
    private val derivativeSettingsCache = ConcurrentHashMap<String, Any>()

    fun setProperty(key: String, value: Any?) {
        if (value == null) {
            properties.clearProperty(key)
        } else {
            properties.setProperty(key, value)
        }
        settingsCache.remove(key)
    }

    fun getInt(key: String, defaultValue: Int): Int = cached(key) { value ->
        value?.trim()?.takeIf(String::isNotEmpty)?.toInt() ?: defaultValue
    } as Int

    fun getLong(key: String, defaultValue: Long): Long = cached(key) { value ->
        value?.trim()?.takeIf(String::isNotEmpty)?.toLong() ?: defaultValue
    } as Long

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = cached(key) { value ->
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return@cached defaultValue
        when (normalized.lowercase(Locale.ROOT)) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> throw IllegalArgumentException("Invalid boolean value for $key: $value")
        }
    } as Boolean

    fun getString(key: String, defaultValue: String?): String? =
        cached(key) { value -> value ?: defaultValue } as? String

    fun derived(key: String, loader: Function<String, Any>): Any? =
        derivativeSettingsCache.computeIfAbsent(key) { loader.apply(it) }

    fun invalidateDerived(key: String) {
        derivativeSettingsCache.remove(key)
    }

    fun setPropertyLookup(propertyLookup: Function<String, String?>) {
        this.propertyLookup = requireNotNull(propertyLookup)
    }

    private fun cached(key: String, defaultValue: (String?) -> Any?): Any? =
        settingsCache.computeIfAbsent(key, Function { lookupKey ->
            defaultValue(propertyLookup.apply(lookupKey))
        })
}

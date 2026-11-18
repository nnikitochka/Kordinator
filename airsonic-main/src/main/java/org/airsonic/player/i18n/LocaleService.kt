package org.airsonic.player.i18n

import org.airsonic.player.service.SettingsService
import java.util.Locale

/** Resolves user and server locale preferences without depending on a web framework. */
class LocaleService(private val settingsService: SettingsService) {
    @Volatile
    private var availableLocales: Set<Locale>? = null

    fun resolveLocale(username: String?): Locale {
        if (username != null) {
            val userLocale = settingsService.getUserSettings(username)?.locale
            if (userLocale != null && localeExists(userLocale)) return userLocale
        }

        val systemLocale = settingsService.locale
        return if (localeExists(systemLocale)) systemLocale else Locale.ENGLISH
    }

    private fun localeExists(locale: Locale): Boolean {
        var locales = availableLocales
        if (locales == null) {
            synchronized(this) {
                locales = availableLocales
                if (locales == null) {
                    locales = settingsService.availableLocales.toSet()
                    availableLocales = locales
                }
            }
        }
        return locales!!.contains(locale)
    }
}

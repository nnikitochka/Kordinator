package org.airsonic.player.service.settings

import java.io.IOException
import java.util.Locale
import org.airsonic.player.domain.Theme
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory

/** Provides locale and theme preferences plus the available presentation options. */
internal class SettingsPresentationPreferences(private val values: SettingsValueStore) : SettingsPresentationAccess {
    private var themes: List<Theme>? = null
    private var locales: List<Locale>? = null

    override fun getLocale(): Locale = Locale.of(
        requireNotNull(values.getString(KEY_LOCALE_LANGUAGE, DEFAULT_LOCALE_LANGUAGE)),
        requireNotNull(values.getString(KEY_LOCALE_COUNTRY, "")),
        requireNotNull(values.getString(KEY_LOCALE_VARIANT, "")),
    )

    override fun setLocale(locale: Locale) {
        values.setProperty(KEY_LOCALE_LANGUAGE, locale.language)
        values.setProperty(KEY_LOCALE_COUNTRY, locale.country)
        values.setProperty(KEY_LOCALE_VARIANT, locale.variant)
    }

    override fun getThemeId(): String = requireNotNull(values.getString(KEY_THEME_ID, DEFAULT_THEME_ID))

    override fun setThemeId(themeId: String?) {
        values.setProperty(KEY_THEME_ID, themeId)
    }

    @Synchronized
    override fun getAvailableThemes(): Array<Theme> {
        if (themes == null) {
            val discovered = mutableListOf<Theme>()
            try {
                val input = SettingsService::class.java.getResourceAsStream(THEMES_FILE)
                for (line in StringUtil.readLines(input)) {
                    val elements = StringUtil.split(line)
                    when (elements.size) {
                        2 -> discovered.add(Theme(elements[0], elements[1]))
                        3 -> discovered.add(Theme(elements[0], elements[1], elements[2]))
                        else -> log.warn("Failed to parse theme from line: [{}].", line)
                    }
                }
            } catch (exception: IOException) {
                log.error("Failed to resolve list of themes.", exception)
                discovered.add(Theme("default", "Airsonic default"))
            }
            themes = discovered
        }
        return themes!!.toTypedArray()
    }

    @Synchronized
    override fun getAvailableLocales(): Array<Locale> {
        if (locales == null) {
            val discovered = mutableListOf<Locale>()
            try {
                val input = SettingsService::class.java.getResourceAsStream(LOCALES_FILE)
                for (line in StringUtil.readLines(input)) {
                    discovered.add(requireNotNull(StringUtil.parseLocale(line)))
                }
            } catch (exception: IOException) {
                log.error("Failed to resolve list of locales.", exception)
                discovered.add(Locale.ENGLISH)
            }
            locales = discovered
        }
        return locales!!.toTypedArray()
    }

    override fun getBrand(): String = "Airsonic"

    private companion object {
        const val KEY_LOCALE_LANGUAGE = "LocaleLanguage"
        const val KEY_LOCALE_COUNTRY = "LocaleCountry"
        const val KEY_LOCALE_VARIANT = "LocaleVariant"
        const val KEY_THEME_ID = "Theme"
        const val DEFAULT_LOCALE_LANGUAGE = "en"
        const val DEFAULT_THEME_ID = "default"
        const val THEMES_FILE = "/org/airsonic/player/theme/themes.txt"
        const val LOCALES_FILE = "/org/airsonic/player/i18n/locales.txt"
        val log = LoggerFactory.getLogger(SettingsPresentationPreferences::class.java)
    }
}

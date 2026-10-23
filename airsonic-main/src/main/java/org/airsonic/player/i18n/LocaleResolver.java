package org.airsonic.player.i18n;

import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Locale resolver implementation which returns the locale selected in the settings.
 */
@Service
public class LocaleResolver implements org.springframework.web.servlet.LocaleResolver {

    private Set<Locale> locales;
    private SecurityService securityService;
    private SettingsService settingsService;

    public LocaleResolver(SecurityService securityService, SettingsService settingsService) {
        this.securityService = securityService;
        this.settingsService = settingsService;
    }

    /**
    * Resolve the current locale via the given request.
    *
    * @param request Request to be used for resolution.
    * @return The current locale.
    */
    @NotNull
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Locale locale = (Locale) request.getAttribute("airsonic.locale");
        if (locale != null) {
            return locale;
        }

        // Look for user-specific locale.
        String username = securityService.getCurrentUsername(request);

        locale = resolveLocale(username);

        // Optimization: Cache locale in the request.
        request.setAttribute("airsonic.locale", locale);

        return locale;
    }

    public Locale resolveLocale(String username) {
        Locale locale = null;
        if (username != null) {
            UserSettings userSettings = settingsService.getUserSettings(username);
            if (userSettings != null) {
                locale = userSettings.getLocale();
            }
        }

        if (locale != null && localeExists(locale)) {
            return locale;
        }

        // Return system locale.
        locale = settingsService.getLocale();
        return localeExists(locale) ? locale : Locale.ENGLISH;
    }

    /**
     * Returns whether the given locale exists.
     * @param locale The locale.
     * @return Whether the locale exists.
     */
    private synchronized boolean localeExists(Locale locale) {
        // Lazily create set of locales.
        if (locales == null) {
            locales = new HashSet<>(Arrays.asList(settingsService.getAvailableLocales()));
        }

        return locales.contains(locale);
    }

    @Override
    public void setLocale(@Nonnull HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("Cannot change locale - use a different locale resolution strategy");
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }
}

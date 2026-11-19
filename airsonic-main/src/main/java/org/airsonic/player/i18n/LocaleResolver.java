package org.airsonic.player.i18n;

import org.airsonic.player.service.SecurityService;
import org.jetbrains.annotations.NotNull;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/** Servlet request adapter for the framework-neutral {@link LocaleService}. */
public class LocaleResolver {
    private final SecurityService securityService;
    private final LocaleService localeService;

    public LocaleResolver(SecurityService securityService, LocaleService localeService) {
        this.securityService = securityService;
        this.localeService = localeService;
    }

    @NotNull
    public Locale resolveLocale(HttpServletRequest request) {
        Locale locale = (Locale) request.getAttribute("airsonic.locale");
        if (locale == null) {
            locale = localeService.resolveLocale(securityService.getCurrentUsername(request));
            request.setAttribute("airsonic.locale", locale);
        }
        return locale;
    }

    public Locale resolveLocale(String username) {
        return localeService.resolveLocale(username);
    }

}

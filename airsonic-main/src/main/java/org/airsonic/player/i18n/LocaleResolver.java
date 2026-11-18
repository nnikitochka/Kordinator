package org.airsonic.player.i18n;

import org.airsonic.player.service.SecurityService;
import org.jetbrains.annotations.NotNull;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Locale;

/** Spring MVC adapter for the framework-neutral {@link LocaleService}. */
public class LocaleResolver implements org.springframework.web.servlet.LocaleResolver {
    private final SecurityService securityService;
    private final LocaleService localeService;

    public LocaleResolver(SecurityService securityService, LocaleService localeService) {
        this.securityService = securityService;
        this.localeService = localeService;
    }

    @NotNull
    @Override
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

    @Override
    public void setLocale(@Nonnull HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("Cannot change locale - use a different locale resolution strategy");
    }
}

package org.airsonic.player.controller;

import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the right frame.
 */
@Controller
@RequestMapping("/right")
public class RightController {
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final VersionService versionService;

    public RightController(SettingsService settingsService, SecurityService securityService, VersionService versionService) {
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.versionService = versionService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        ModelAndView result = new ModelAndView("right");

        UserSettings userSettings = settingsService.getUserSettings(securityService.getCurrentUsername(request));
        if (userSettings.getFinalVersionNotificationEnabled() && versionService.isNewFinalVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestFinalVersion());

        } else if (userSettings.getBetaVersionNotificationEnabled() && versionService.isNewBetaVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestBetaVersion());
        }

        map.put("brand", settingsService.getBrand());
        map.put("showNowPlaying", userSettings.getShowNowPlayingEnabled());
        map.put("user", securityService.getCurrentUser(request));

        result.addObject("model", map);
        return result;
    }

}

package org.airsonic.player.controller;

import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the top frame.
 */
@Controller
@RequestMapping("/top")
public class TopController {
    private final SettingsService settingsService;
    private final SecurityService securityService;

    public TopController(SettingsService settingsService, SecurityService securityService) {
        this.settingsService = settingsService;
        this.securityService = securityService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();

        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = settingsService.getUserSettings(user.getUsername());

        map.put("user", user);
        map.put("showSideBar", userSettings.getShowSideBar());
        map.put("showAvatar", userSettings.getAvatarScheme() != AvatarScheme.NONE);
        return new ModelAndView("top","model", map);
    }
}

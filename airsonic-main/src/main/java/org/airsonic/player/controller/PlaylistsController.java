package org.airsonic.player.controller;

import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the playlists page.
 */
@Controller
@RequestMapping("/playlists")
public class PlaylistsController {
    private final SecurityService securityService;
    private final SettingsService settingsService;

    public PlaylistsController(SecurityService securityService, SettingsService settingsService) {
        this.securityService = securityService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String doGet(HttpServletRequest request, Model model) {
        Map<String, Object> map = new HashMap<>();

        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = settingsService.getUserSettings(user.getUsername());

        map.put("username", user.getUsername());
        map.put("viewAsList", userSettings.getViewAsList());
        map.put("initialPaginationSize", userSettings.getPaginationSizePlaylist());
        model.addAttribute("model", map);
        return "playlists";
    }
}

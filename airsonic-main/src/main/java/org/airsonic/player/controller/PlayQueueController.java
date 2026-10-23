package org.airsonic.player.controller;

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the playlist frame.
 */
@Controller
@RequestMapping("/playQueue")
public class PlayQueueController {
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final SettingsService settingsService;

    public PlayQueueController(PlayerService playerService, SecurityService securityService, SettingsService settingsService) {
        this.playerService = playerService;
        this.securityService = securityService;
        this.settingsService = settingsService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = settingsService.getUserSettings(user.getUsername());

        Map<String, Object> map = new HashMap<>();
        map.put("user", user);
        map.put("players", playerService.getPlayersForUserAndClientId(user.getUsername(), null));
        map.put("visibility", userSettings.getPlayqueueVisibility());
        map.put("partyMode", userSettings.getPartyModeEnabled());
        map.put("notify", userSettings.getSongNotificationEnabled());
        map.put("autoHide", userSettings.getAutoHidePlayQueue());
        map.put("initialPaginationSize", userSettings.getPaginationSizePlayqueue());
        map.put("autoBookmark", userSettings.getAutoBookmark());
        map.put("audioBookmarkFrequency", userSettings.getAudioBookmarkFrequency());
        return new ModelAndView("playQueue", "model", map);
    }

    @GetMapping("/player")
    @ResponseBody
    public Map<String, Object> getPlayer(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Player player = playerService.getPlayer(request, response);
        return ImmutableMap.of("id", player.getId(), "description", player.getShortDescription(), "tech", player.getTechnology());
    }
}

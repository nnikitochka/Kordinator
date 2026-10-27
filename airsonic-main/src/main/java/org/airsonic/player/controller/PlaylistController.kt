package org.airsonic.player.controller;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the playlist page.
 */
@Controller
@RequestMapping("/playlist")
public class PlaylistController {
    private final SecurityService securityService;
    private final PlaylistService playlistService;
    private final SettingsService settingsService;
    private final PlayerService playerService;

    public PlaylistController(SecurityService securityService, PlaylistService playlistService, SettingsService settingsService, PlayerService playerService) {
        this.securityService = securityService;
        this.playlistService = playlistService;
        this.settingsService = settingsService;
        this.playerService = playerService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<>();

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        User user = securityService.getCurrentUser(request);
        String username = user.getUsername();
        UserSettings userSettings = settingsService.getUserSettings(username);
        Player player = playerService.getPlayer(request, response);
        Playlist playlist = playlistService.getPlaylist(id);
        if (playlist == null) {
            return new ModelAndView(new RedirectView("notFound"));
        }

        map.put("playlist", playlist);
        map.put("user", user);
        map.put("player", player);
        map.put("visibility", userSettings.getPlaylistVisibility());
        map.put("editAllowed", username.equals(playlist.getUsername()) || securityService.isAdmin(username));
        map.put("partyMode", userSettings.getPartyModeEnabled());
        map.put("initialPaginationSize", userSettings.getPaginationSizePlaylist());

        return new ModelAndView("playlist","model",map);
    }
}

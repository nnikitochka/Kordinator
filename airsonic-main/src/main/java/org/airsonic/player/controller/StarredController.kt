package org.airsonic.player.controller;

import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for showing a user's starred items.
 */
@Controller
@RequestMapping("/starred")
public class StarredController {
    private final PlayerService playerService;
    private final MediaFileDao mediaFileDao;
    private final SecurityService securityService;
    private final SettingsService settingsService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;

    public StarredController(PlayerService playerService, MediaFileDao mediaFileDao, SecurityService securityService, SettingsService settingsService, MediaFileService mediaFileService, MediaFolderService mediaFolderService) {
        this.playerService = playerService;
        this.mediaFileDao = mediaFileDao;
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<>();

        User user = securityService.getCurrentUser(request);
        String username = user.getUsername();
        UserSettings userSettings = settingsService.getUserSettings(username);
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        List<MediaFile> artists = mediaFileDao.getStarredDirectories(0, Integer.MAX_VALUE, username, musicFolders);
        List<MediaFile> albums = mediaFileDao.getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders);
        List<MediaFile> files = mediaFileDao.getStarredFiles(0, Integer.MAX_VALUE, username, musicFolders);
        mediaFileService.populateStarredDate(artists, username);
        mediaFileService.populateStarredDate(albums, username);
        mediaFileService.populateStarredDate(files, username);

        List<MediaFile> songs = new ArrayList<>();
        List<MediaFile> videos = new ArrayList<>();
        for (MediaFile file : files) {
            (file.isVideo() ? videos : songs).add(file);
        }

        map.put("user", user);
        map.put("partyModeEnabled", userSettings.getPartyModeEnabled());
        map.put("player", playerService.getPlayer(request, response));
        map.put("coverArtSize", CoverArtScheme.MEDIUM.getSize());
        map.put("artists", artists);
        map.put("albums", albums);
        map.put("songs", songs);
        map.put("videos", videos);
        return new ModelAndView("starred","model",map);
    }
}

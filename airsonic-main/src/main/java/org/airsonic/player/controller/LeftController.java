package org.airsonic.player.controller;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.*;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.util.StringUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.*;

/**
 * Controller for the left index frame.
 */
@Controller
@RequestMapping("/left")
public class LeftController {
    private final MediaScannerService mediaScannerService;
    private final IndexManager indexManager;
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final MusicIndexService musicIndexService;
    private final PlayerService playerService;
    private final MediaFolderService mediaFolderService;
    private final MusicIndexStateService musicIndexStateService;

    public LeftController(MediaScannerService mediaScannerService, IndexManager indexManager, SettingsService settingsService, SecurityService securityService, MusicIndexService musicIndexService, PlayerService playerService, MediaFolderService mediaFolderService, MusicIndexStateService musicIndexStateService) {
        this.mediaScannerService = mediaScannerService;
        this.indexManager = indexManager;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.musicIndexService = musicIndexService;
        this.playerService = playerService;
        this.mediaFolderService = mediaFolderService;
        this.musicIndexStateService = musicIndexStateService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer musicFolderId = ServletRequestUtils.getIntParameter(request, "musicFolderId");
        boolean musicFolderChanged = musicFolderId != null && musicIndexStateService.selectMusicFolder(
                securityService.getCurrentUsername(request), musicFolderId);
        Map<String, Object> map = new HashMap<>();

        MediaLibraryStatistics statistics = indexManager.getStatistics();
        Locale locale = RequestContextUtils.getLocale(request);

        boolean refresh = ServletRequestUtils.getBooleanParameter(request, "refresh", false);
        if (refresh) {
            mediaFolderService.clearMusicFolderCache();
        }

        String username = securityService.getCurrentUsername(request);
        UserSettings userSettings = settingsService.getUserSettings(username);
        List<MusicFolder> allMusicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MusicFolder> musicFoldersToUse = mediaFolderService.getMusicFoldersForUser(username, userSettings.getSelectedMusicFolderId());
        MusicFolder selectedMusicFolder = musicFoldersToUse.stream()
                .filter(f -> f.getId().equals(userSettings.getSelectedMusicFolderId()))
                .findAny().orElse(null);
        MusicFolderContent musicFolderContent = musicIndexService.getMusicFolderContent(musicFoldersToUse, refresh);

        map.put("player", playerService.getPlayer(request, response));
        map.put("scanning", mediaScannerService.isScanning());
        map.put("musicFolders", allMusicFolders);
        map.put("selectedMusicFolder", selectedMusicFolder);
        map.put("shortcuts", musicIndexService.getShortcuts(musicFoldersToUse));
        map.put("partyMode", userSettings.getPartyModeEnabled());
        map.put("organizeByFolderStructure", settingsService.isOrganizeByFolderStructure());
        map.put("musicFolderChanged", musicFolderChanged);

        if (statistics != null) {
            map.put("statistics", statistics);
            long bytes = statistics.getTotalLengthInBytes();
            long hours = (long) (statistics.getTotalDurationInSeconds() / 3600L);
            map.put("hours", hours);
            map.put("bytes", StringUtil.formatBytes(bytes, locale));
        }

        map.put("indexedArtists", musicFolderContent.getIndexedArtists());
        map.put("singleSongs", musicFolderContent.getSingleSongs());
        map.put("indexes", musicFolderContent.getIndexedArtists().keySet());
        map.put("user", securityService.getCurrentUser(request));

        return new ModelAndView("left","model",map);
    }

}

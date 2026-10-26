package org.airsonic.player.controller;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.*;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.StringUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Controller for the left index frame.
 */
@Controller
@RequestMapping("/left")
public class LeftController {
    // Update this time if you want to force a refresh in clients.
    private static final Instant LAST_COMPATIBILITY_TIME = Instant.parse("2012-03-06T00:00:00.00Z");

    private final MediaScannerService mediaScannerService;
    private final IndexManager indexManager;
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final MusicIndexService musicIndexService;
    private final PlayerService playerService;
    private final MediaFolderService mediaFolderService;

    public LeftController(MediaScannerService mediaScannerService, IndexManager indexManager, SettingsService settingsService, SecurityService securityService, MusicIndexService musicIndexService, PlayerService playerService, MediaFolderService mediaFolderService) {
        this.mediaScannerService = mediaScannerService;
        this.indexManager = indexManager;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.musicIndexService = musicIndexService;
        this.playerService = playerService;
        this.mediaFolderService = mediaFolderService;
    }

    /**
     * Note: This class intentionally does not implement org.springframework.web.servlet.mvc.LastModified
     * as we don't need browser-side caching of left.jsp.  This method is only used by RESTController.
     */
    public long getLastModified(HttpServletRequest request) throws Exception {
        saveSelectedMusicFolder(request);

        if (mediaScannerService.isScanning()) {
            return -1L;
        }

        long lastModified = LAST_COMPATIBILITY_TIME.toEpochMilli();
        String username = securityService.getCurrentUsername(request);
        UserSettings userSettings = settingsService.getUserSettings(username);

        // When was settings last changed?
        lastModified = Math.max(lastModified, settingsService.getSettingsChanged());

        // When was music folder(s) on disk last changed?

        List<MusicFolder> allMusicFolders = mediaFolderService.getMusicFoldersForUser(username);
        MusicFolder selectedMusicFolder = allMusicFolders.stream()
                .filter(f -> f.getId().equals(userSettings.getSelectedMusicFolderId()))
                .findAny().orElse(null);
        if (selectedMusicFolder != null) {
            Path file = selectedMusicFolder.getPath();
            lastModified = Math.max(lastModified, FileUtil.lastModified(file).toEpochMilli());
        } else {
            for (MusicFolder musicFolder : allMusicFolders) {
                Path file = musicFolder.getPath();
                lastModified = Math.max(lastModified, FileUtil.lastModified(file).toEpochMilli());
            }
        }

        // When was music folder table last changed?
        for (MusicFolder musicFolder : allMusicFolders) {
            lastModified = Math.max(lastModified, musicFolder.getChanged().toEpochMilli());
        }

        // When was user settings last changed?
        lastModified = Math.max(lastModified, userSettings.getChanged().toEpochMilli());

        return lastModified;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        boolean musicFolderChanged = saveSelectedMusicFolder(request);
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

    private boolean saveSelectedMusicFolder(HttpServletRequest request) throws Exception {
        Integer musicFolderId = ServletRequestUtils.getIntParameter(request, "musicFolderId");
        if (musicFolderId == null) {
            return false;
        }
        // Note: UserSettings.setChanged() is intentionally not called. This would break browser caching
        // of the left frame.
        UserSettings settings = settingsService.getUserSettings(securityService.getCurrentUsername(request));
        settings.setSelectedMusicFolderId(musicFolderId);
        settingsService.updateUserSettings(settings);

        return true;
    }
}

package org.airsonic.player.controller;

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Controller for the "more" page.
 */
@Controller
@RequestMapping("/more")
public class MoreController {
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;

    public MoreController(SettingsService settingsService, SecurityService securityService, PlayerService playerService, MediaFileService mediaFileService, MediaFolderService mediaFolderService) {
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<>();

        User user = securityService.getCurrentUser(request);

        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(user.getUsername());
        Supplier<Map<String, Object>> contextSupplier = () -> {
            Map<String, Object> context = settingsService.buildSpelContext();
            if (StringUtils.isNotEmpty(user.getUsername())) {
                context.put("USER_NAME", user.getUsername());
                context.put("USER_MUSIC_FOLDERS", musicFolders.stream().map(MusicFolder::getPath).map(Path::toString).collect(Collectors.toList()));
            }
            return context;
        };
        String uploadsFolder = settingsService.getUploadsFolder();
        // The default upload pattern uses the first accessible music folder. Spring 6's
        // SpEL rejects indexing an empty list, whereas a freshly installed server has no
        // music folders yet. Keep the page usable until the administrator creates one.
        String uploadDirectory = musicFolders.isEmpty() && StringUtils.contains(uploadsFolder, "USER_MUSIC_FOLDERS")
                ? ""
                : settingsService.resolveContextualString(uploadsFolder, contextSupplier);

        Player player = playerService.getPlayer(request, response);
        ModelAndView result = new ModelAndView();
        result.addObject("model", map);
        map.put("user", user);
        map.put("uploadDirectory", uploadDirectory);
        map.put("genres", mediaFileService.getGenres(false));
        map.put("currentYear", LocalDate.now().getYear());
        map.put("musicFolders", musicFolders);
        map.put("clientSidePlaylist", player.isExternalWithPlaylist() || player.isWeb());
        map.put("brand", settingsService.getBrand());
        return result;
    }
}

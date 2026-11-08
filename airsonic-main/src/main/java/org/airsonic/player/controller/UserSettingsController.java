package org.airsonic.player.controller;

import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.Util;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the page used to administrate users.
 */
@Controller
@RequestMapping("/userSettings")
public class UserSettingsController {
    private final SecurityService securityService;
    private final SettingsService settingsService;
    private final MediaFolderService mediaFolderService;
    private final TranscodingService transcodingService;

    public UserSettingsController(SecurityService securityService, SettingsService settingsService, MediaFolderService mediaFolderService, TranscodingService transcodingService) {
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.mediaFolderService = mediaFolderService;
        this.transcodingService = transcodingService;
    }

    @GetMapping
    protected String displayForm(HttpServletRequest request, Model model) throws Exception {
        UserSettingsCommand command;
        if (!model.containsAttribute("command")) {
            command = new UserSettingsCommand();

            User user = getUser(request);
            if (user != null) {
                command.setUser(user);
                command.setEmail(user.getEmail());
                UserSettings userSettings = settingsService.getUserSettings(user.getUsername());
                command.setTranscodeSchemeName(userSettings.getTranscodeScheme().name());
                command.setAllowedMusicFolderIds(Util.toIntArray(getAllowedMusicFolderIds(user)));
                command.setCurrentUser(securityService.getCurrentUser(request).getUsername().equals(user.getUsername()));
            } else {
                command.setNewUser(true);
                command.setStreamRole(true);
                command.setSettingsRole(true);
            }

        } else {
            command = (UserSettingsCommand) model.asMap().get("command");
        }
        command.setUsers(securityService.getAllUsers());
        command.setTranscodingSupported(transcodingService.isDownsamplingSupported(null));
        command.setTranscodeDirectory(SettingsService.getTranscodeDirectory().toString());
        command.setTranscodeSchemes(TranscodeScheme.values());
        command.setLdapEnabled(settingsService.isLdapEnabled());
        command.setAllMusicFolders(mediaFolderService.getAllMusicFolders());
        model.addAttribute("command", command);
        return "userSettings";
    }

    private User getUser(HttpServletRequest request) throws ServletRequestBindingException {
        Integer userIndex = ServletRequestUtils.getIntParameter(request, "userIndex");
        if (userIndex != null) {
            List<User> allUsers = securityService.getAllUsers();
            if (userIndex >= 0 && userIndex < allUsers.size()) {
                return allUsers.get(userIndex);
            }
        }
        return null;
    }

    private List<Integer> getAllowedMusicFolderIds(User user) {
        List<Integer> result = new ArrayList<>();
        List<MusicFolder> allowedMusicFolders = user == null
                ? mediaFolderService.getAllMusicFolders()
                : mediaFolderService.getMusicFoldersForUser(user.getUsername());

        for (MusicFolder musicFolder : allowedMusicFolders) {
            result.add(musicFolder.getId());
        }
        return result;
    }

}

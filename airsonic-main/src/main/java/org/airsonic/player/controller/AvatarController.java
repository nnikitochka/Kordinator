package org.airsonic.player.controller;

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.LastModified;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller which produces avatar images.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/avatar")
public class AvatarController implements LastModified {

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private ResourceLoader loader;

    @Override
    public long getLastModified(@NotNull HttpServletRequest request) {
        Avatar avatar = getAvatar(request);
        long result = avatar == null ? -1L : avatar.getCreatedDate().toEpochMilli();

        String username = request.getParameter("username");
        if (username != null) {
            UserSettings userSettings = settingsService.getUserSettings(username);
            result = Math.max(result, userSettings.getChanged().toEpochMilli());
        }

        return result;
    }

    @GetMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Avatar avatar = getAvatar(request);

        if (avatar == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(avatar.getMimeType());
        Resource res = loader.getResource(avatar.getPath().toString());
        if (!res.exists()) {
            res = loader.getResource("file:" + avatar.getPath().toString());
        }
        IOUtils.copy(res.getInputStream(), response.getOutputStream());
    }

    private Avatar getAvatar(HttpServletRequest request) {
        String id = request.getParameter("id");
        boolean forceCustom = ServletRequestUtils.getBooleanParameter(request, "forceCustom", false);

        if (id != null) {
            return settingsService.getSystemAvatar(Integer.parseInt(id));
        }

        String username = request.getParameter("username");
        if (username == null) {
            return null;
        }

        UserSettings userSettings = settingsService.getUserSettings(username);
        if (userSettings.getAvatarScheme() == AvatarScheme.CUSTOM || forceCustom) {
            return settingsService.getCustomAvatar(username);
        }
        if (userSettings.getAvatarScheme() == AvatarScheme.NONE) {
            return null;
        }
        return settingsService.getSystemAvatar(userSettings.getSystemAvatarId());
    }

}

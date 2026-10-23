package org.airsonic.player.controller;

import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.UPnPService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the page used to administrate the UPnP/DLNA server settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/dlnaSettings")
public class DLNASettingsController {

    private UPnPService upnpService;
    private SettingsService settingsService;

    public DLNASettingsController(UPnPService upnpService, SettingsService settingsService) {
        this.upnpService = upnpService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String handleGet(Model model) {

        Map<String, Object> map = new HashMap<>();

        map.put("dlnaEnabled", settingsService.isDlnaEnabled());
        map.put("dlnaServerName", settingsService.getDlnaServerName());
        map.put("dlnaBaseLANURL", settingsService.getDlnaBaseLANURL());

        model.addAttribute("model", map);
        return "dlnaSettings";
    }

    @PostMapping
    public String handlePost(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        handleParameters(request);
        redirectAttributes.addFlashAttribute("settings_toast", true);
        return "redirect:dlnaSettings.view";
    }

    private void handleParameters(HttpServletRequest request) {
        boolean dlnaEnabled = ServletRequestUtils.getBooleanParameter(request, "dlnaEnabled", false);
        String dlnaServerName = StringUtils.trimToNull(request.getParameter("dlnaServerName"));
        String dlnaBaseLANURL = StringUtils.trimToNull(request.getParameter("dlnaBaseLANURL"));
        if (dlnaServerName == null) {
            dlnaServerName = "Airsonic";
        }

        upnpService.setMediaServerEnabled(false);
        settingsService.setDlnaEnabled(dlnaEnabled);
        settingsService.setDlnaServerName(dlnaServerName);
        settingsService.setDlnaBaseLANURL(dlnaBaseLANURL);
        settingsService.save();
        upnpService.setMediaServerEnabled(dlnaEnabled);
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setUpnpService(UPnPService upnpService) {
        this.upnpService = upnpService;
    }
}

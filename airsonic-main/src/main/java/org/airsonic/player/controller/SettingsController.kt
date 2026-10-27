package org.airsonic.player.controller;

import org.airsonic.player.domain.User;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for the main settings page.
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final SecurityService securityService;

    public SettingsController(SecurityService securityService) {
        this.securityService = securityService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) {
        User user = securityService.getCurrentUser(request);

        // Redirect to music folder settings if admin.
        String view = user.isAdminRole() ? "musicFolderSettings" : "personalSettings";

        return new ModelAndView(new RedirectView(view));
    }
}

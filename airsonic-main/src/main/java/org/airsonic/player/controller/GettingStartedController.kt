package org.airsonic.player.controller;

import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/gettingStarted")
public class GettingStartedController {

    private final SettingsService settingsService;

    public GettingStartedController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ModelAndView gettingStarted(HttpServletRequest request) {

        if (request.getParameter("hide") != null) {
            settingsService.setGettingStartedEnabled(false);
            settingsService.save();
            return new ModelAndView(new RedirectView("home.view"));
        }

        Map<String, Object> map = new HashMap<>();
        map.put("runningAsRoot", "root".equals(System.getProperty("user.name")));
        return new ModelAndView("gettingStarted", "model", map);
    }

}

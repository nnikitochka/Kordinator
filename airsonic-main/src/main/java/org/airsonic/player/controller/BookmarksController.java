package org.airsonic.player.controller;

import org.airsonic.player.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;
import java.util.Map;

@Controller
@RequestMapping("/bookmarks")
public class BookmarksController {
    private final SettingsService settingsService;

    public BookmarksController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ModelAndView doGet(Principal user, HttpServletRequest request) {
        return new ModelAndView("bookmarks", "model",
                Map.of("initialPaginationSize",
                        settingsService.getUserSettings(user.getName()).getPaginationSizeBookmarks()));
    }
}

package org.airsonic.player.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Serves the user administration page; its data is loaded from the REST API. */
@Controller
@RequestMapping("/userSettings")
public class UserSettingsController {
    @GetMapping
    protected String displayForm() {
        return "userSettings";
    }
}

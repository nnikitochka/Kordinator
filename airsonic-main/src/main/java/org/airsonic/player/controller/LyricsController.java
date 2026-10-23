package org.airsonic.player.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the lyrics popup.
 */
@Controller
@RequestMapping("/lyrics")
public class LyricsController {
    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> map = new HashMap<>();

        map.put("artist", request.getParameter("artist"));
        map.put("song", request.getParameter("song"));

        return new ModelAndView("lyrics","model",map);
    }
}

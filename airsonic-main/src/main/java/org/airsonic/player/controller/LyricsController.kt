package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for the lyrics popup.
 */
@Controller
@RequestMapping("/lyrics")
class LyricsController {
    @GetMapping
    protected fun handleRequestInternal(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val map = mapOf(
            "artist" to request.getParameter("artist"),
            "song" to request.getParameter("song"),
        )

        return ModelAndView("lyrics", "model", map)
    }
}

package org.airsonic.player.controller.cover

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("/coverArtJsp")
class CoverArtJspController {
    @GetMapping
    fun get(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        return ModelAndView("coverArt", request.parameterMap)
    }
}

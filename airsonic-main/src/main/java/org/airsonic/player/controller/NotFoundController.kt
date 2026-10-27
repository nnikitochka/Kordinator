package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping("/notFound")
class NotFoundController {
    @GetMapping
    fun notFound(request: HttpServletRequest?, response: HttpServletResponse?): ModelAndView {
        return ModelAndView("notFound")
    }
}

package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping("/accessDenied")
class AccessDeniedController {
    @GetMapping
    fun accessDenied(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        logger.info("The IP ${request.remoteAddr} tried to access the forbidden url ${request.requestURL}.")
        return ModelAndView("accessDenied")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AccessDeniedController::class.java)
    }
}

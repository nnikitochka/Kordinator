package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SecurityService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * Controller for updating music file ratings.
 */
@Controller
@RequestMapping("/setRating")
class SetRatingController(
    private val ratingService: RatingService,
    private val securityService: SecurityService,
    private val mediaFileService: MediaFileService
) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest): ModelAndView {
        val id = ServletRequestUtils.getRequiredIntParameter(request, "id")
        var rating = ServletRequestUtils.getIntParameter(request, "rating")
        if (rating == 0) {
            rating = null
        }

        val mediaFile = mediaFileService.getMediaFile(id)
        val username = securityService.getCurrentUsername(request)
        ratingService.setRatingForUser(username, mediaFile, rating)

        return ModelAndView(RedirectView("main.view?id=$id"))
    }
}

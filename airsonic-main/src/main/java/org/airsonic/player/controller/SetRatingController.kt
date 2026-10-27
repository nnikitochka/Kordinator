package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for updating music file ratings.
 */
@Controller
@RequestMapping("/setRating")
public class SetRatingController {
    private final RatingService ratingService;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;

    public SetRatingController(RatingService ratingService, SecurityService securityService, MediaFileService mediaFileService) {
        this.ratingService = ratingService;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) throws Exception {
        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        Integer rating = ServletRequestUtils.getIntParameter(request, "rating");
        if (rating == 0) {
            rating = null;
        }
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        String username = securityService.getCurrentUsername(request);
        ratingService.setRatingForUser(username, mediaFile, rating);

        return new ModelAndView(new RedirectView("main.view?id=" + id));
    }
}

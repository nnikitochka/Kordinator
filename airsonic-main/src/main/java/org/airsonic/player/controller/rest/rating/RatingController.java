package org.airsonic.player.controller.rest.rating;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class RatingController {
    private final RestPlayerResolver resolver;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;
    private final RatingService ratingService;
    private final JAXBWriter writer;

    public RatingController(RestPlayerResolver resolver, SecurityService securityService,
            MediaFileService mediaFileService, RatingService ratingService, JAXBWriter writer) {
        this.resolver = resolver;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.ratingService = ratingService;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER,
                "Required param (" + e.getParameterName() + ") is missing"));
    }

    @RequestMapping("/setRating")
    public void setRating(HttpServletRequest request, HttpServletResponse response) throws Exception {
        resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        Integer rating = getRequiredIntParameter(request, "rating");
        if (rating == 0) {
            rating = null;
        }

        int id = getRequiredIntParameter(request, "id");
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.NOT_FOUND, "File not found: " + id));
            return;
        }

        String username = securityService.getCurrentUsername(request);
        ratingService.setRatingForUser(username, mediaFile, rating);

        writer.writeResponse(request, response, writer.createResponse(true));
    }
}

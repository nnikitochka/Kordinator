package org.airsonic.player.controller.rest.artists;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;

/** Spring adapter retained as a separate migration step before switching this endpoint to Ktor. */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class ArtistsController {
    private final ArtistsService artistsService;
    private final SecurityService securityService;
    private final RestPlayerResolver playerResolver;
    private final JAXBWriter jaxbWriter;

    public ArtistsController(ArtistsService artistsService, SecurityService securityService,
                             RestPlayerResolver playerResolver, JAXBWriter jaxbWriter) {
        this.artistsService = artistsService;
        this.securityService = securityService;
        this.playerResolver = playerResolver;
        this.jaxbWriter = jaxbWriter;
    }

    @RequestMapping("/getArtists")
    public void getArtists(HttpServletRequest request, HttpServletResponse response) {
        // Preserve the REST player creation previously performed by wrapRequest().
        playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        Response result = jaxbWriter.createResponse(true);
        result.setArtists(artistsService.getArtists(securityService.getCurrentUsername(request)));
        jaxbWriter.writeResponse(request, response, result);
    }
}

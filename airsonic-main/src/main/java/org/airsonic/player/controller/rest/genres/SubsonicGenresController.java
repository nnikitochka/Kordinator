package org.airsonic.player.controller.rest.genres;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;

/** Spring adapter retained as a separate migration step before switching this endpoint to Ktor. */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicGenresController {
    private final SubsonicGenresService genresService;
    private final RestPlayerResolver playerResolver;
    private final JAXBWriter jaxbWriter;

    public SubsonicGenresController(SubsonicGenresService genresService,
                                    RestPlayerResolver playerResolver, JAXBWriter jaxbWriter) {
        this.genresService = genresService;
        this.playerResolver = playerResolver;
        this.jaxbWriter = jaxbWriter;
    }

    @RequestMapping("/getGenres")
    public void getGenres(HttpServletRequest request, HttpServletResponse response) {
        // Preserve the REST player creation previously performed by wrapRequest().
        playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        Response result = jaxbWriter.createResponse(true);
        result.setGenres(genresService.getGenres());
        jaxbWriter.writeResponse(request, response, result);
    }
}

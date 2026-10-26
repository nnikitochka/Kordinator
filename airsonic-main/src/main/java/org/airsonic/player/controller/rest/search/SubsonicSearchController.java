package org.airsonic.player.controller.rest.search;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/** Servlet adapter for the Subsonic search API during the Spring-to-Ktor migration. */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicSearchController {
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final RestPlayerResolver playerResolver;
    private final SubsonicSearchService searchService;
    private final JAXBWriter jaxbWriter;

    public SubsonicSearchController(SecurityService securityService, PlayerService playerService,
                                    RestPlayerResolver playerResolver, SubsonicSearchService searchService,
                                    JAXBWriter jaxbWriter) {
        this.securityService = securityService;
        this.playerService = playerService;
        this.playerResolver = playerResolver;
        this.searchService = searchService;
        this.jaxbWriter = jaxbWriter;
    }

    @RequestMapping("/search")
    public void search(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = withRestPlayer(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);
        jaxbWriter.writeResponse(request, response, searchService.search(
                username,
                player,
                request.getParameter("any"),
                request.getParameter("artist"),
                request.getParameter("album"),
                request.getParameter("title"),
                ServletRequestUtils.getIntParameter(request, "count", 20),
                ServletRequestUtils.getIntParameter(request, "offset", 0))
        );
    }

    @RequestMapping("/search2")
    public void search2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = withRestPlayer(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);
        jaxbWriter.writeResponse(request, response, searchService.search2(
                username,
                player,
                request.getParameter("query"),
                ServletRequestUtils.getIntParameter(request, "musicFolderId"),
                ServletRequestUtils.getIntParameter(request, "artistCount", 20),
                ServletRequestUtils.getIntParameter(request, "artistOffset", 0),
                ServletRequestUtils.getIntParameter(request, "albumCount", 20),
                ServletRequestUtils.getIntParameter(request, "albumOffset", 0),
                ServletRequestUtils.getIntParameter(request, "songCount", 20),
                ServletRequestUtils.getIntParameter(request, "songOffset", 0))
        );
    }

    @RequestMapping("/search3")
    public void search3(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = withRestPlayer(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);
        jaxbWriter.writeResponse(request, response, searchService.search3(
                username,
                player,
                request.getParameter("query"),
                ServletRequestUtils.getIntParameter(request, "musicFolderId"),
                ServletRequestUtils.getIntParameter(request, "artistCount", 20),
                ServletRequestUtils.getIntParameter(request, "artistOffset", 0),
                ServletRequestUtils.getIntParameter(request, "albumCount", 20),
                ServletRequestUtils.getIntParameter(request, "albumOffset", 0),
                ServletRequestUtils.getIntParameter(request, "songCount", 20),
                ServletRequestUtils.getIntParameter(request, "songOffset", 0))
        );
    }

    private HttpServletRequest withRestPlayer(HttpServletRequest request) {
        String clientId = request.getParameter("c");
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), clientId, request.getRemoteAddr(), false);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if ("player".equals(name)) {
                    return playerId == null ? null : String.valueOf(playerId);
                }
                return super.getParameter(name);
            }
        };
    }
}

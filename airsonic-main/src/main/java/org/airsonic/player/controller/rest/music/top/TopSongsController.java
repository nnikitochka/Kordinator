package org.airsonic.player.controller.rest.music.top;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.TopSongs;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class TopSongsController {
    private final RestPlayerResolver resolver;
    private final PlayerService players;
    private final SecurityService security;
    private final MediaFolderService folders;
    private final LastFmService lastFm;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public TopSongsController(RestPlayerResolver resolver, PlayerService players, SecurityService security,
            MediaFolderService folders, LastFmService lastFm, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.resolver = resolver;
        this.players = players;
        this.security = security;
        this.folders = folders;
        this.lastFm = lastFm;
        this.mapper = mapper;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER,
                "Required param (" + e.getParameterName() + ") is missing"));
    }

    @RequestMapping("/getTopSongs")
    public void getTopSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
        String username = security.getCurrentUsername(request);
        String artist = getRequiredStringParameter(request, "artist");
        int count = getIntParameter(request, "count", 50);
        var songs = lastFm.getTopSongs(artist, count, folders.getMusicFoldersForUser(username));
        Player player = players.getPlayer(request, response);
        TopSongs topSongs = new TopSongs();
        for (var song : songs) {
            topSongs.getSong().add(mapper.child(player, song, username));
        }
        Response result = writer.createResponse(true);
        result.setTopSongs(topSongs);
        writer.writeResponse(request, response, result);
    }
}

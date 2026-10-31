package org.airsonic.player.controller.rest.music.song;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;

import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SongController {
    private final RestPlayerResolver resolver;
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final MediaFileDao mediaFileDao;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public SongController(RestPlayerResolver resolver, PlayerService playerService, SecurityService securityService,
            MediaFileDao mediaFileDao, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.resolver = resolver;
        this.playerService = playerService;
        this.securityService = securityService;
        this.mediaFileDao = mediaFileDao;
        this.mapper = mapper;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER,
                "Required param (" + e.getParameterName() + ") is missing"));
    }

    @RequestMapping("/getSong")
    public void getSong(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        MediaFile song = mediaFileDao.getMediaFile(id);
        if (song == null || song.isDirectory()) {
            writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.NOT_FOUND, "Song not found."));
            return;
        }
        if (!securityService.isFolderAccessAllowed(song, username)) {
            writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Access denied"));
            return;
        }

        Response res = writer.createResponse(true);
        res.setSong(mapper.child(player, song, username));
        writer.writeResponse(request, response, res);
    }
}

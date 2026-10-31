package org.airsonic.player.controller.rest.music.genre;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Songs;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SongsByGenreController {
    private final RestPlayerResolver resolver;
    private final PlayerService players;
    private final SecurityService security;
    private final MediaFolderService folders;
    private final MediaFileDao mediaFiles;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public SongsByGenreController(RestPlayerResolver resolver, PlayerService players, SecurityService security,
            MediaFolderService folders, MediaFileDao mediaFiles, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.resolver = resolver; this.players = players; this.security = security;
        this.folders = folders; this.mediaFiles = mediaFiles; this.mapper = mapper; this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER,
                "Required param (" + e.getParameterName() + ") is missing"));
    }

    @RequestMapping("/getSongsByGenre")
    public void getSongsByGenre(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
        Player player = players.getPlayer(request, response);
        String username = security.getCurrentUsername(request);
        String genre = getRequiredStringParameter(request, "genre");
        int offset = getIntParameter(request, "offset", 0);
        int count = Math.clamp(getIntParameter(request, "count", 10), 0, 500);
        Integer folderId = getIntParameter(request, "musicFolderId");
        Songs songs = new Songs();
        for (var file : mediaFiles.getSongsByGenre(genre, offset, count, folders.getMusicFoldersForUser(username, folderId))) {
            songs.getSong().add(mapper.child(player, file, username));
        }
        Response result = writer.createResponse(true);
        result.setSongsByGenre(songs);
        writer.writeResponse(request, response, result);
    }
}

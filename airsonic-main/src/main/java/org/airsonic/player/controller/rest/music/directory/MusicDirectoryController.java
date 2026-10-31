package org.airsonic.player.controller.rest.music.directory;

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
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Directory;
import org.subsonic.restapi.Response;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class MusicDirectoryController {
    private final RestPlayerResolver resolver;
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;
    private final MediaFileDao mediaFileDao;
    private final RatingService ratingService;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public MusicDirectoryController(RestPlayerResolver resolver, PlayerService playerService, SecurityService securityService,
            MediaFileService mediaFileService, MediaFileDao mediaFileDao, RatingService ratingService,
            SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.resolver = resolver;
        this.playerService = playerService;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.mediaFileDao = mediaFileDao;
        this.ratingService = ratingService;
        this.mapper = mapper;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER,
                "Required param (" + e.getParameterName() + ") is missing"));
    }

    @RequestMapping("/getMusicDirectory")
    public void getMusicDirectory(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        MediaFile dir = mediaFileService.getMediaFile(id);
        if (dir == null) {
            writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.NOT_FOUND, "Directory not found"));
            return;
        }
        if (!securityService.isFolderAccessAllowed(dir, username)) {
            writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Access denied"));
            return;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        Directory directory = new Directory();
        directory.setId(String.valueOf(id));
        try {
            if (!mediaFileService.isRoot(parent)) {
                directory.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        directory.setName(dir.getName());
        directory.setStarred(writer.convertDate(mediaFileDao.getMediaFileStarredDate(id, username)));
        directory.setPlayCount((long) dir.getPlayCount());
        directory.setAccentColor(mapper.accentColor(dir, parent));

        if (dir.isAlbum()) {
            directory.setAverageRating(ratingService.getAverageRating(dir));
            directory.setUserRating(ratingService.getRatingForUser(username, dir));
        }

        for (MediaFile child : mediaFileService.getChildrenOf(dir, true, true, true)) {
            directory.getChild().add(mapper.child(player, child, username));
        }

        Response res = writer.createResponse(true);
        res.setDirectory(directory);
        writer.writeResponse(request, response, res);
    }

}

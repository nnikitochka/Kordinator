package org.airsonic.player.controller.rest.videos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Videos;

import java.util.List;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class VideosController {
    private final RestPlayerResolver resolver;
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final MediaFolderService mediaFolderService;
    private final MediaFileDao mediaFileDao;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public VideosController(RestPlayerResolver resolver, PlayerService playerService, SecurityService securityService,
            MediaFolderService mediaFolderService, MediaFileDao mediaFileDao, SubsonicResponseMapper mapper,
            JAXBWriter writer) {
        this.resolver = resolver;
        this.playerService = playerService;
        this.securityService = securityService;
        this.mediaFolderService = mediaFolderService;
        this.mediaFileDao = mediaFileDao;
        this.mapper = mapper;
        this.writer = writer;
    }

    @RequestMapping("/getVideos")
    public void getVideos(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int size = getIntParameter(request, "size", Integer.MAX_VALUE);
        int offset = getIntParameter(request, "offset", 0);
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        Videos result = new Videos();
        for (MediaFile mediaFile : mediaFileDao.getVideos(size, offset, musicFolders)) {
            result.getVideo().add(mapper.child(player, mediaFile, username));
        }
        Response res = writer.createResponse(true);
        res.setVideos(result);
        writer.writeResponse(request, response, res);
    }
}

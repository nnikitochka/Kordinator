package org.airsonic.player.controller.rest.music.random;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Songs;
import java.util.List;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class RandomSongsController {
    private final RestPlayerResolver resolver;
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final MediaFolderService mediaFolderService;
    private final SearchService searchService;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public RandomSongsController(RestPlayerResolver resolver, PlayerService playerService, SecurityService securityService,
            MediaFolderService mediaFolderService, SearchService searchService, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.resolver = resolver;
        this.playerService = playerService;
        this.securityService = securityService;
        this.mediaFolderService = mediaFolderService;
        this.searchService = searchService;
        this.mapper = mapper;
        this.writer = writer;
    }

    @RequestMapping("/getRandomSongs")
    public void getRandomSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int size = getIntParameter(request, "size", 10);
        size = Math.max(0, Math.min(size, 500));
        String genre = getStringParameter(request, "genre");
        Integer fromYear = getIntParameter(request, "fromYear");
        Integer toYear = getIntParameter(request, "toYear");
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);
        RandomSearchCriteria criteria = new RandomSearchCriteria(size, genre, fromYear, toYear, musicFolders);

        Songs result = new Songs();
        for (MediaFile mediaFile : searchService.getRandomSongs(criteria)) {
            result.getSong().add(mapper.child(player, mediaFile, username));
        }
        Response res = writer.createResponse(true);
        res.setRandomSongs(result);
        writer.writeResponse(request, response, res);
    }

}

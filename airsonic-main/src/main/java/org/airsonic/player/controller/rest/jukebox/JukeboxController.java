package org.airsonic.player.controller.rest.jukebox;

import com.google.common.primitives.Ints;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JukeboxService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.JukeboxPlaylist;
import org.subsonic.restapi.JukeboxStatus;
import org.subsonic.restapi.Response;
import java.util.List;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller("restJukeboxController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class JukeboxController {
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final PlayQueueService playQueueService;
    private final JukeboxService jukeboxService;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper responseMapper;
    private final JAXBWriter jaxbWriter;

    public JukeboxController(SecurityService securityService, PlayerService playerService, PlayQueueService playQueueService,
                             JukeboxService jukeboxService, RestPlayerResolver playerResolver,
                             SubsonicResponseMapper responseMapper, JAXBWriter jaxbWriter) {
        this.securityService = securityService; this.playerService = playerService; this.playQueueService = playQueueService;
        this.jukeboxService = jukeboxService; this.playerResolver = playerResolver;
        this.responseMapper = responseMapper; this.jaxbWriter = jaxbWriter;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        error(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + e.getParameterName() + ") is missing");
    }

    @RequestMapping("/jukeboxControl")
    public void jukeboxControl(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);

        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isJukeboxRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to use jukebox.");
            return;
        }

        Player player = playerService.getPlayer(request, response);

        boolean returnPlaylist = false;
        String action = getRequiredStringParameter(request, "action");

        switch (action) {
            case "start":
                playQueueService.start(player);
                break;
            case "stop":
                playQueueService.stop(player);
                break;
            case "skip":
                int index = getRequiredIntParameter(request, "index");
                long offset = getLongParameter(request, "offset", 0) * 1000;
                playQueueService.skip(player, index, offset);
                break;
            case "add":
                int[] ids = getIntParameters(request, "id");
                playQueueService.add(player, Ints.asList(ids), null, true, true);
                break;
            case "set":
                ids = getIntParameters(request, "id");
                playQueueService.reset(player, Ints.asList(ids), true);
                break;
            case "clear":
                playQueueService.clear(player);
                break;
            case "remove":
                index = getRequiredIntParameter(request, "index");
                playQueueService.remove(player, List.of(index));
                break;
            case "shuffle":
                playQueueService.shuffle(player);
                break;
            case "setGain":
                float gain = getRequiredFloatParameter(request, "gain");
                playQueueService.setJukeboxGain(player, gain);
                break;
            case "get":
                returnPlaylist = true;
                break;
            case "status":
                // No action necessary.
                break;
            default:
                throw new Exception("Unknown jukebox action: '" + action + "'.");
        }

        String username = securityService.getCurrentUsername(request);
        PlayQueue playQueue = player.getPlayQueue();

        // this variable is only needed for the JukeboxLegacySubsonicService. To be removed.
        boolean controlsJukebox = jukeboxService.canControl(player);

        int currentIndex = controlsJukebox && !playQueue.isEmpty() ? playQueue.getIndex() : -1;
        boolean playing = controlsJukebox && !playQueue.isEmpty() && playQueue.getStatus() == PlayQueue.Status.PLAYING;
        float gain;
        int position;
        gain = jukeboxService.getGain(player);
        position = controlsJukebox && !playQueue.isEmpty() ? jukeboxService.getPosition(player) : 0;

        Response res = createResponse();
        if (returnPlaylist) {
            JukeboxPlaylist result = new JukeboxPlaylist();
            res.setJukeboxPlaylist(result);
            result.setCurrentIndex(currentIndex);
            result.setPlaying(playing);
            result.setGain(gain);
            result.setPosition(position);
            for (MediaFile mediaFile : playQueue.getFiles()) {
                result.getEntry().add(createJaxbChild(player, mediaFile, username));
            }
        } else {
            JukeboxStatus result = new JukeboxStatus();
            res.setJukeboxStatus(result);
            result.setCurrentIndex(currentIndex);
            result.setPlaying(playing);
            result.setGain(gain);
            result.setPosition(position);
        }

        jaxbWriter.writeResponse(request, response, res);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request, boolean jukebox) {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), jukebox);
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                if ("player".equals(name)) return playerId == null ? null : String.valueOf(playerId);
                return super.getParameter(name);
            }
        };
    }
    private org.subsonic.restapi.Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return responseMapper.child(player, mediaFile, username);
    }
    private Response createResponse() { return jaxbWriter.createResponse(true); }
    private void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) {
        jaxbWriter.writeErrorResponse(request, response, code, message);
    }
}

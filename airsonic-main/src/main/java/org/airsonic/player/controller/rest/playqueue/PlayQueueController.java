package org.airsonic.player.controller.rest.playqueue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.SavedPlayQueue;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller("restPlayQueueController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class PlayQueueController {
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final PlayQueueDao playQueueDao;
    private final MediaFileService mediaFileService;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper responseMapper;
    private final JAXBWriter jaxbWriter;

    public PlayQueueController(SecurityService securityService, PlayerService playerService, PlayQueueDao playQueueDao,
                               MediaFileService mediaFileService, RestPlayerResolver playerResolver,
                               SubsonicResponseMapper responseMapper, JAXBWriter jaxbWriter) {
        this.securityService = securityService;
        this.playerService = playerService;
        this.playQueueDao = playQueueDao;
        this.mediaFileService = mediaFileService;
        this.playerResolver = playerResolver;
        this.responseMapper = responseMapper;
        this.jaxbWriter = jaxbWriter;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        error(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + e.getParameterName() + ") is missing");
    }

    @RequestMapping("/getPlayQueue")
    public void getPlayQueue(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response);

        SavedPlayQueue playQueue = playQueueDao.getPlayQueue(username);
        if (playQueue == null) {
            writeEmptyResponse(request, response);
            return;
        }

        org.subsonic.restapi.PlayQueue restPlayQueue = new org.subsonic.restapi.PlayQueue();
        restPlayQueue.setUsername(playQueue.getUsername());
        restPlayQueue.setCurrent(playQueue.getCurrentMediaFileId());
        restPlayQueue.setPosition(playQueue.getPositionMillis());
        restPlayQueue.setChanged(jaxbWriter.convertDate(playQueue.getChanged()));
        restPlayQueue.setChangedBy(playQueue.getChangedBy());

        for (Integer mediaFileId : playQueue.getMediaFileIds()) {
            MediaFile mediaFile = mediaFileService.getMediaFile(mediaFileId);
            if (mediaFile != null) {
                restPlayQueue.getEntry().add(createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlayQueue(restPlayQueue);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/savePlayQueue")
    public void savePlayQueue(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        List<Integer> mediaFileIds = Arrays.stream(getIntParameters(request, "id")).boxed().collect(toList());
        Integer current = getIntParameter(request, "current");
        Long position = getLongParameter(request, "position");
        String changedBy = getRequiredStringParameter(request, "c");

        if (!mediaFileIds.contains(current)) {
            error(request, response, ErrorCode.GENERIC, "Current track is not included in play queue");
            return;
        }

        SavedPlayQueue playQueue = new SavedPlayQueue(null, username, mediaFileIds, current, position, Instant.now(), changedBy);
        playQueueDao.savePlayQueue(playQueue);
        writeEmptyResponse(request, response);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
    }
    private org.subsonic.restapi.Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return responseMapper.child(player, mediaFile, username);
    }
    private Response createResponse() { return jaxbWriter.createResponse(true); }
    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) { jaxbWriter.writeResponse(request, response, createResponse()); }
    private void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) { jaxbWriter.writeErrorResponse(request, response, code, message); }
}

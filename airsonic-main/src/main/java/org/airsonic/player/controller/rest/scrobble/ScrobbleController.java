package org.airsonic.player.controller.rest.scrobble;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.service.AudioScrobblerService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import java.time.Instant;
import java.util.UUID;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class ScrobbleController {
    private static final Logger LOG = LoggerFactory.getLogger(ScrobbleController.class);
    private final RestPlayerResolver resolver;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final StatusService statusService;
    private final AudioScrobblerService audioScrobblerService;
    private final JAXBWriter writer;

    public ScrobbleController(RestPlayerResolver resolver, PlayerService playerService, MediaFileService mediaFileService,
            StatusService statusService, AudioScrobblerService audioScrobblerService, JAXBWriter writer) {
        this.resolver = resolver;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.statusService = statusService;
        this.audioScrobblerService = audioScrobblerService;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.MISSING_PARAMETER,
                "Required param (" + e.getParameterName() + ") is missing"));
    }

    @RequestMapping("/scrobble")
    public void scrobble(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };

        Player player = playerService.getPlayer(request, response);

        boolean submission = getBooleanParameter(request, "submission", true);
        int[] ids = getRequiredIntParameters(request, "id");
        long[] times = getLongParameters(request, "time");
        if (times.length > 0 && times.length != ids.length) {
            writer.writeResponse(request, response, writer.createErrorResponse(ErrorCode.GENERIC, "Wrong number of timestamps: " + times.length));
            return;
        }

        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            MediaFile file = mediaFileService.getMediaFile(id);
            if (file == null) {
                LOG.warn("File to scrobble not found: {}", id);
                continue;
            }
            Instant time = times.length == 0 ? null : Instant.ofEpochMilli(times[i]);

            statusService.addRemotePlay(new PlayStatus(UUID.randomUUID(), file, player, time == null ? Instant.now() : time));
            mediaFileService.incrementPlayCount(file);
            audioScrobblerService.register(file, player.getUsername(), submission, time);
        }

        writer.writeResponse(request, response, writer.createResponse(true));
    }

}

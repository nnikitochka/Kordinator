package org.airsonic.player.controller.rest.nowplaying;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.ajax.playing.NowPlayingInfo;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.service.StatusService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.NowPlaying;
import org.subsonic.restapi.NowPlayingEntry;
import org.subsonic.restapi.Response;
import java.util.stream.Stream;

@Controller("restNowPlayingController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class NowPlayingController {
    private final RestPlayerResolver resolver;
    private final StatusService statusService;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public NowPlayingController(RestPlayerResolver resolver, StatusService statusService, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.resolver = resolver;
        this.statusService = statusService;
        this.mapper = mapper;
        this.writer = writer;
    }

    @RequestMapping("/getNowPlaying")
    public void getNowPlaying(HttpServletRequest request, HttpServletResponse response) {
        resolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        NowPlaying result = new NowPlaying();

        Stream.concat(statusService.getActivePlays().parallelStream(),
                statusService.getInactivePlays().parallelStream())
            .map(NowPlayingInfo::fromPlayStatus)
            .forEach(s -> {
                NowPlayingEntry entry = new NowPlayingEntry();
                entry.setUsername(s.getPlayer().getUsername());
                entry.setPlayerId(s.getPlayer().getId());
                entry.setPlayerName(s.getPlayer().getName());
                entry.setMinutesAgo((int) s.getMinutesAgo());
                result.getEntry().add(mapper.child(entry, s.getPlayer(), s.getMediaFile(), entry.getUsername()));
            });

        Response res = writer.createResponse(true);
        res.setNowPlaying(result);
        writer.writeResponse(request, response, res);
    }

}

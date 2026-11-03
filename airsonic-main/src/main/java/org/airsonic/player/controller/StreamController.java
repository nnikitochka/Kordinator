package org.airsonic.player.controller;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaStreamService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.spring.KnownLengthInputStreamResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;

import java.awt.Dimension;
import java.util.Optional;

/** Website and external-player adapter for monitored media streams. */
@Controller
@RequestMapping({ "/stream/**", "/ext/stream/**" })
public class StreamController {
    private final MediaStreamService streams;
    private final PlayerService playerService;
    private final SecurityService securityService;

    public StreamController(MediaStreamService streams, PlayerService playerService, SecurityService securityService) {
        this.streams = streams;
        this.playerService = playerService;
        this.securityService = securityService;
    }

    @GetMapping
    public ResponseEntity<Resource> handleRequest(Authentication authentication,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String suffix,
            @RequestParam Optional<Integer> maxBitRate,
            @RequestParam Optional<Integer> id,
            @RequestParam Optional<String> path,
            @RequestParam(required = false) Double offsetSeconds,
            ServletWebRequest swr) throws Exception {
        Player player = playerService.getPlayer(swr.getRequest(), swr.getResponse(), false, true);
        User user = securityService.getUserByName(player.getUsername());
        var request = swr.getRequest();
        MediaStreamService.Result result;
        try {
            result = streams.open(authentication, player, user, format, suffix, maxBitRate, id, path, offsetSeconds,
                    new MediaStreamService.VideoOptions(request.getParameter("maxBitRate"), request.getParameter("timeOffset"),
                            request.getParameter("duration"), request.getParameter("size")),
                    swr.getHeader("icy-metadata"), swr::checkNotModified);
        } catch (NumberFormatException e) {
            throw new ServletRequestBindingException("Invalid video stream parameter", e);
        }
        if (result == null) return null;
        Resource resource = result.getLength() == null ? new InputStreamResource(result.getStream())
                : new KnownLengthInputStreamResource(result.getStream(), result.getLength());
        HttpHeaders headers = new HttpHeaders();
        result.getHeaders().forEach(headers::put);
        headers.setContentType(MediaType.parseMediaType(result.getContentType()));
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    public static Dimension getRequestedVideoSize(String sizeSpec) {
        return MediaStreamService.getRequestedVideoSize(sizeSpec);
    }
}

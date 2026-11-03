package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.NetworkService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.hls.HlsPlaylistService;
import org.airsonic.player.service.hls.HlsSegmentService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.web.client.HttpServerErrorException;
import java.util.concurrent.TimeoutException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Controller which produces the HLS (Http Live Streaming) playlist.
 */
@Controller("hlsController")
@RequestMapping({ "/hls", "/ext/hls" })
public class HLSController {
    private final HlsPlaylistService playlistService;
    private final HlsSegmentService segmentService;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final SecurityService securityService;

    public HLSController(PlayerService playerService, MediaFileService mediaFileService, SecurityService securityService, HlsPlaylistService playlistService, HlsSegmentService segmentService) {
        this.playlistService = playlistService;
        this.segmentService = segmentService;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.securityService = securityService;
    }

    @GetMapping("/hls.m3u8")
    public void handleHlsRequest(Authentication authentication,
            @RequestParam Integer id,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        Player player = playerService.getPlayer(request, response);
        String username = player.getUsername();

        if (mediaFile == null || mediaFile.isDirectory()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Media file not found or incorrect type: " + id);
            return;
        }

        if (!(authentication instanceof JWTAuthenticationToken)
                && !securityService.isFolderAccessAllowed(mediaFile, username)) {
            throw new AccessDeniedException("Access to file " + mediaFile.getId() + " is forbidden for user " + username);
        }

        Double duration = mediaFile.getDuration();
        if (duration == null || duration < 0.0001) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unknown duration for media file: " + id);
            return;
        }

        response.setContentType("application/x-mpegurl");
        response.setCharacterEncoding(StringUtil.ENCODING_UTF8);
        response.setHeader("Access-Control-Allow-Origin", "*");
        String requestWithoutContextPath = request.getRequestURI().substring(request.getContextPath().length() + 1);
        String prefix = StringUtils.removeEndIgnoreCase(requestWithoutContextPath, "/hls.m3u8");
        response.getWriter().write(playlistService.generate(authentication, mediaFile, player,
                NetworkService.getBaseUrl(request), prefix, request.getParameterValues("maxBitRate"),
                request.getParameter("size")));
    }

    @GetMapping("/segment/**")
    public ResponseEntity<Resource> handleSegmentRequest(Authentication auth,
            @RequestParam int id,
            @RequestParam int segmentIndex,
            @RequestParam(name = "player") String playerId,
            @RequestParam int maxBitRate,
            @RequestParam String size,
            @RequestParam(required = false) @DefaultValue("10") Integer duration,
            @RequestParam(required = false) Integer audioTrack,
            ServletWebRequest swr) throws Exception {
        Player player = playerService.getPlayer(swr.getRequest(), swr.getResponse());
        User user = securityService.getUserByName(auth.getName());
        Resource segment;
        try {
            segment = segmentService.getSegment(auth, user, player, id, playerId, segmentIndex, maxBitRate, size, duration, audioTrack, false, true);
        } catch (TimeoutException e) {
            throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccessControlAllowOrigin("*");
        headers.setContentType(MediaType.parseMediaType("video/MP2T"));
        headers.setContentDisposition(ContentDisposition.inline().filename("f.ts").build());
        return ResponseEntity.ok().headers(headers).body(segment);
    }
}

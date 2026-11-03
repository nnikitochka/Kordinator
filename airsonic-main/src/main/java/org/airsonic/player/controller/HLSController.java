package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.io.PipeStreams.MonitoredResource;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.NetworkService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.service.hls.HlsSession;
import org.airsonic.player.service.hls.HlsPlaylistService;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Controller which produces the HLS (Http Live Streaming) playlist.
 */
@Controller("hlsController")
@RequestMapping({ "/hls", "/ext/hls" })
public class HLSController {
    private static final Logger LOG = LoggerFactory.getLogger(HLSController.class);

    private final HlsPlaylistService playlistService;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final SecurityService securityService;
    private final StatusService statusService;
    private final SettingsService settingsService;
    private final TranscodingService transcodingService;

    private final Map<HlsSession.Key, HlsSession> sessions = new ConcurrentHashMap<>();

    public HLSController(PlayerService playerService, MediaFileService mediaFileService, SecurityService securityService, StatusService statusService, SettingsService settingsService, TranscodingService transcodingService, HlsPlaylistService playlistService) {
        this.playlistService = playlistService;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.securityService = securityService;
        this.statusService = statusService;
        this.settingsService = settingsService;
        this.transcodingService = transcodingService;
    }

    @PostConstruct
    public void init() {
        FileUtil.delete(HlsSession.getHlsRootDirectory());
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
        MediaFile mediaFile = this.mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            throw new NotFoundException("Media file not found: " + id);
        }
        Player player = this.playerService.getPlayer(swr.getRequest(), swr.getResponse());
        User user = securityService.getUserByName(auth.getName());
        if (!(auth instanceof JWTAuthenticationToken)
                && !securityService.isFolderAccessAllowed(mediaFile, user.getUsername())) {
            throw new AccessDeniedException("Access to file " + id + " is forbidden for user " + user.getUsername());
        }
        TransferStatus status = this.statusService.createStreamStatus(player);
        status.setMediaFile(mediaFile);
        HlsSession.Key sessionKey = new HlsSession.Key(id, playerId, maxBitRate, size, duration, audioTrack);
        HlsSession session = getOrCreateSession(sessionKey, mediaFile);
        Path segmentFile = session.waitForSegment(segmentIndex, 30000L);
        if (segmentFile == null) {
            throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Timed out producing segment " + segmentIndex + " for media file " + id);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccessControlAllowOrigin("*");
        headers.setContentType(MediaType.parseMediaType("video/MP2T"));
        headers.setContentDisposition(ContentDisposition.inline().filename("f.ts").build());

        Supplier<TransferStatus> statusSupplier = () -> status;
        Consumer<TransferStatus> statusCloser = s -> {
            securityService.updateUserByteCounts(user, s.getBytesTransferred(), 0L, 0L);
            statusService.removeStreamStatus(s);
        };
        BiConsumer<InputStream, TransferStatus> inputStreamInit = (i, s) -> {
            LOG.info("{}: {} listening to {}", player.getIpAddress(), player.getUsername(), FileUtil.getShortPath(mediaFile.getRelativePath()));
            if (segmentIndex == 0)
                this.mediaFileService.incrementPlayCount(mediaFile);
        };

        Resource resource = new MonitoredResource(new PathResource(segmentFile),
                settingsService.getDownloadBitrateLimiter(), statusSupplier, statusCloser, inputStreamInit);

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    private HlsSession getOrCreateSession(HlsSession.Key sessionKey, MediaFile mediaFile) {
        return this.sessions.computeIfAbsent(sessionKey, k -> {
            for (HlsSession.Key k1 : sessions.keySet()) {
                if (k1.getMediaFileId() == k.getMediaFileId()
                        && StringUtils.equals(k1.getPlayerId(), k.getPlayerId())) {
                    sessions.remove(k1).destroySession();
                }
            }

            return new HlsSession(k, mediaFile, transcodingService);
        });
    }

}

package org.airsonic.player.controller;

import com.google.common.io.MoreFiles;
import org.airsonic.player.domain.*;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.airsonic.player.service.*;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Controller for the page used to play shared music (Twitter, Facebook etc).
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/ext/share")
public class ExternalPlayerController {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalPlayerController.class);

    private final PlayerService playerService;
    private final ShareService shareService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final JWTSecurityService jwtSecurityService;
    private final VideoPlayerController videoPlayerController;

    public ExternalPlayerController(PlayerService playerService, ShareService shareService, MediaFileService mediaFileService, MediaFolderService mediaFolderService, JWTSecurityService jwtSecurityService, VideoPlayerController videoPlayerController) {
        this.playerService = playerService;
        this.shareService = shareService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.jwtSecurityService = jwtSecurityService;
        this.videoPlayerController = videoPlayerController;
    }

    @GetMapping("/{shareName}")
    protected ModelAndView handleRequestInternal(
            Authentication authentication,
            @PathVariable("shareName") String shareName,
            HttpServletRequest request,
            HttpServletResponse response)
            throws Exception {
        LOG.debug("Share name is {}", shareName);

        if (StringUtils.isBlank(shareName)) {
            LOG.warn("Could not find share with shareName {}", shareName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        Share share = shareService.getShareByName(shareName);

        if (share != null && share.getExpires() != null && share.getExpires().isBefore(Instant.now())) {
            LOG.warn("Share {} has expired", shareName);
            share = null;
        }

        if (share != null) {
            share.setLastVisited(Instant.now());
            share.setVisitCount(share.getVisitCount() + 1);
            shareService.updateShare(share);
        }

        Player player = playerService.getGuestPlayer(request);

        Instant expires = authentication instanceof JWTAuthenticationToken ? JWTSecurityService.getExpiration((JWTAuthenticationToken) authentication) : null;

        Map<String, Object> map = new HashMap<>();

        List<MediaFileWithUrlInfo> media = getMedia(request, share, player, expires);
        map.put("share", share);
        map.put("media", media);
        map.put("videoPresent", media.stream().anyMatch(mf -> mf.getFile().isVideo()));

        return new ModelAndView("externalPlayer", "model", map);
    }

    private List<MediaFileWithUrlInfo> getMedia(HttpServletRequest request, Share share, Player player, Instant expires) {
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(player.getUsername());

        if (share != null) {
            return shareService.getSharedFiles(share.getId(), musicFolders)
                    .stream()
                    .filter(f -> Files.exists(f.getFullPath(mediaFolderService.getMusicFolderById(f.getFolderId()).getPath())))
                    .flatMap(f -> {
                        if (f.isDirectory()) {
                            return mediaFileService.getChildrenOf(f, true, false, true).stream()
                                    .map(fc -> addUrlInfo(request, player, fc, expires));
                        } else {
                            return Stream.of(addUrlInfo(request, player, f, expires));
                        }
                    })
                    .collect(toList());
        }

        return emptyList();
    }

    public MediaFileWithUrlInfo addUrlInfo(HttpServletRequest request, Player player, MediaFile mediaFile, Instant expires) {
        String prefix = "ext";
        String baseUrl = NetworkService.getBaseUrl(request);

        boolean streamable = true;
        String contentType = StringUtil.getMimeType(MoreFiles.getFileExtension(mediaFile.getRelativePath()));
        String streamUrl = baseUrl + jwtSecurityService
                .addJWTToken(User.USERNAME_GUEST, UriComponentsBuilder.fromUriString(prefix + "/stream")
                        .queryParam("id", mediaFile.getId())
                        .queryParam("player", player.getId())
                        .queryParam("format", "raw"),
                expires).build().toUriString();
        if (mediaFile.isVideo()) {
            streamable = videoPlayerController.isStreamable(mediaFile);
            if (!streamable) {
                contentType = "application/x-mpegurl";
                streamUrl = baseUrl + jwtSecurityService
                        .addJWTToken(User.USERNAME_GUEST, UriComponentsBuilder.fromUriString(prefix + "/hls/hls.m3u8")
                                .queryParam("id", mediaFile.getId()).queryParam("player", player.getId())
                                .queryParam("maxBitRate", VideoPlayerController.BIT_RATES),
                        expires).build().toUriString();
            }
        }

        String captionsUrl = baseUrl + jwtSecurityService.addJWTToken(
                User.USERNAME_GUEST,
                UriComponentsBuilder.fromUriString(prefix + "/captions/list").queryParam("id", mediaFile.getId()),
                expires)
            .build().toUriString();

        String coverArtUrl = baseUrl + jwtSecurityService.addJWTToken(
                User.USERNAME_GUEST,
                UriComponentsBuilder
                        .fromUriString(prefix + "/coverArt.view")
                        .queryParam("id", mediaFile.getId())
                        .queryParam("size", "500"),
                expires)
            .build().toUriString();
        return new MediaFileWithUrlInfo(mediaFile, streamable, coverArtUrl, streamUrl, captionsUrl, contentType);
    }
}

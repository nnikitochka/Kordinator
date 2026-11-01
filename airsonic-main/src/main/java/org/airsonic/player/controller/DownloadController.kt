package org.airsonic.player.controller;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.DownloadResponse;
import org.airsonic.player.service.DownloadService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/download")
public class DownloadController {
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final DownloadService downloads;

    public DownloadController(PlayerService playerService, SecurityService securityService, DownloadService downloads) {
        this.playerService = playerService;
        this.securityService = securityService;
        this.downloads = downloads;
    }

    @GetMapping
    public ResponseEntity<Resource> handleRequest(Principal principal, @RequestParam Optional<Integer> id,
            @RequestParam(required = false) Integer playlist, @RequestParam(required = false) Integer player,
            @RequestParam(required = false, name = "i") List<Integer> indices, ServletWebRequest request) throws Exception {
        User user = securityService.getUserByName(principal.getName());
        Player transferPlayer = playerService.getPlayer(request.getRequest(), request.getResponse(), false, false);
        DownloadResponse download = downloads.prepare(user, transferPlayer, id, playlist, player, indices);
        if (download == null || request.checkNotModified(String.valueOf(download.getSize()), download.getChanged())) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.builder("attachment").filename(download.getFilename(), StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType(StringUtil.getMimeType(FilenameUtils.getExtension(download.getFilename()))));
        return ResponseEntity.ok().headers(headers).body(download.hasContent() ? new DownloadResource(download) : null);
    }
}
